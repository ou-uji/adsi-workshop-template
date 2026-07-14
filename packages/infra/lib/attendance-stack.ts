import * as cdk from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as elbv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3deploy from "aws-cdk-lib/aws-s3-deployment";
import * as cloudfront from "aws-cdk-lib/aws-cloudfront";
import * as origins from "aws-cdk-lib/aws-cloudfront-origins";
import { Construct } from "constructs";
import path = require("path");

export class AttendanceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const prefix = "Team-MIH-MSYS-Kintai";
    const account = this.account;

    // ========================================
    // Backend: ECS Fargate
    // ========================================

    // Default VPC (workshop simplicity)
    const vpc = ec2.Vpc.fromLookup(this, "DefaultVPC", {
      isDefault: true,
    });

    // ECS Cluster
    const cluster = new ecs.Cluster(this, "Cluster", {
      clusterName: `${prefix}-Cluster`,
      vpc,
    });

    // Task Definition
    const taskDefinition = new ecs.FargateTaskDefinition(this, "TaskDef", {
      memoryLimitMiB: 2048,
      cpu: 1024,
      family: `${prefix}-Task`,
    });

    // Docker image from asset with SageMaker network constraint
    // CRITICAL: SageMaker requires --network=sagemaker for docker build
    // This is handled via CDK_DOCKER env var in deploy script
    const backendImage = ecs.ContainerImage.fromAsset(
      path.join(__dirname, "../../backend"),
      {
        file: "Dockerfile",
        buildArgs: {
          BUILDKIT_INLINE_CACHE: "1",
        },
      }
    );

    const container = taskDefinition.addContainer("BackendContainer", {
      image: backendImage,
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: `${prefix}-backend`,
        logRetention: 7, // CloudWatch Logs 保持期間: 7日
      }),
      environment: {
        SPRING_PROFILES_ACTIVE: "workshop", // H2 in-memory
      },
      healthCheck: {
        command: [
          "CMD-SHELL",
          "curl -f http://localhost:8080/api/health || exit 1",
        ],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60),
      },
    });

    container.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // ALB
    const alb = new elbv2.ApplicationLoadBalancer(this, "ALB", {
      loadBalancerName: `${prefix}-ALB`,
      vpc,
      internetFacing: true,
    });

    const targetGroup = new elbv2.ApplicationTargetGroup(this, "TargetGroup", {
      targetGroupName: `${prefix}-TG`,
      vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
      healthCheck: {
        path: "/api/health",
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
      deregistrationDelay: cdk.Duration.seconds(30),
    });

    const listener = alb.addListener("Listener", {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      defaultTargetGroups: [targetGroup],
    });

    // ECS Service
    const service = new ecs.FargateService(this, "Service", {
      serviceName: `${prefix}-Service`,
      cluster,
      taskDefinition,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PUBLIC,
      },
    });

    service.attachToApplicationTargetGroup(targetGroup);

    // ========================================
    // Frontend: S3 + CloudFront
    // ========================================

    // S3 Bucket for static files
    const frontendBucket = new s3.Bucket(this, "FrontendBucket", {
      bucketName: `team-mih-msys-kintai-frontend-${account}`,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      autoDeleteObjects: true,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      encryption: s3.BucketEncryption.S3_MANAGED,
    });

    // Origin Access Identity for CloudFront
    const oai = new cloudfront.OriginAccessIdentity(this, "OAI", {
      comment: `${prefix} CloudFront OAI`,
    });

    frontendBucket.grantRead(oai);

    // CloudFront Distribution
    const distribution = new cloudfront.Distribution(this, "Distribution", {
      comment: `${prefix} CDN`,
      defaultBehavior: {
        origin: new origins.S3Origin(frontendBucket, {
          originAccessIdentity: oai,
        }),
        viewerProtocolPolicy:
          cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        allowedMethods: cloudfront.AllowedMethods.ALLOW_GET_HEAD_OPTIONS,
        cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD_OPTIONS,
        compress: true,
      },
      defaultRootObject: "index.html",
      errorResponses: [
        {
          httpStatus: 404,
          responseHttpStatus: 200,
          responsePagePath: "/index.html",
          ttl: cdk.Duration.seconds(10),
        },
        {
          httpStatus: 403,
          responseHttpStatus: 200,
          responsePagePath: "/index.html",
          ttl: cdk.Duration.seconds(10),
        },
      ],
      additionalBehaviors: {
        "/api/*": {
          origin: new origins.LoadBalancerV2Origin(alb, {
            protocolPolicy: cloudfront.OriginProtocolPolicy.HTTP_ONLY,
          }),
          viewerProtocolPolicy:
            cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
          allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
          cachedMethods: cloudfront.CachedMethods.CACHE_GET_HEAD_OPTIONS,
          cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
          originRequestPolicy:
            cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
        },
      },
    });

    // Deploy frontend static files to S3 via CloudFormation (SageMaker constraint)
    // NOTE: Source directory must exist before deploy. Build script creates it.
    new s3deploy.BucketDeployment(this, "DeployFrontend", {
      sources: [
        s3deploy.Source.asset(path.join(__dirname, "../../frontend/out")),
      ],
      destinationBucket: frontendBucket,
      distribution,
      distributionPaths: ["/*"],
    });

    // ========================================
    // Outputs
    // ========================================

    new cdk.CfnOutput(this, "CloudFrontURL", {
      value: `https://${distribution.distributionDomainName}`,
      description: "CloudFront Distribution URL",
      exportName: `${prefix}-CloudFrontURL`,
    });

    new cdk.CfnOutput(this, "ALB-URL", {
      value: `http://${alb.loadBalancerDnsName}`,
      description: "ALB DNS Name (for debugging)",
      exportName: `${prefix}-ALB-URL`,
    });

    new cdk.CfnOutput(this, "S3BucketName", {
      value: frontendBucket.bucketName,
      description: "Frontend S3 Bucket Name",
      exportName: `${prefix}-S3BucketName`,
    });
  }
}
