import * as cdk from "aws-cdk-lib";
import * as ec2 from "aws-cdk-lib/aws-ec2";
import * as ecs from "aws-cdk-lib/aws-ecs";
import * as ecr_assets from "aws-cdk-lib/aws-ecr-assets";
import * as elbv2 from "aws-cdk-lib/aws-elasticloadbalancingv2";
import * as logs from "aws-cdk-lib/aws-logs";
import { Construct } from "constructs";
import path = require("path");

export class AttendanceStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const prefix = "Team-MIH-MSYS-Kintai";

    // VPC（ワークショップ用 — 最小構成: 2 AZ, public subnet のみ）
    const vpc = new ec2.Vpc(this, "VPC", {
      vpcName: `${prefix}-VPC`,
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: "Public", subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
      ],
    });

    // ECS Cluster
    const cluster = new ecs.Cluster(this, "Cluster", {
      clusterName: `${prefix}-Cluster`,
      vpc,
    });

    // ========================================
    // Backend: Spring Boot on Fargate
    // ========================================

    const backendTaskDef = new ecs.FargateTaskDefinition(this, "BackendTaskDef", {
      memoryLimitMiB: 2048,
      cpu: 1024,
      family: `${prefix}-Backend`,
    });

    const backendContainer = backendTaskDef.addContainer("backend", {
      image: ecs.ContainerImage.fromAsset(
        path.join(__dirname, "../../backend"),
        { file: "Dockerfile", networkMode: ecr_assets.NetworkMode.custom("sagemaker") }
      ),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: `${prefix}-backend`,
        logRetention: logs.RetentionDays.THREE_DAYS,
      }),
      environment: {
        SPRING_PROFILES_ACTIVE: "workshop",
      },
      healthCheck: {
        command: ["CMD-SHELL", "curl -f http://localhost:8080/api/health || exit 1"],
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        retries: 3,
        startPeriod: cdk.Duration.seconds(60),
      },
    });

    backendContainer.addPortMappings({ containerPort: 8080 });

    const backendService = new ecs.FargateService(this, "BackendService", {
      serviceName: `${prefix}-Backend`,
      cluster,
      taskDefinition: backendTaskDef,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // ========================================
    // Frontend: Next.js standalone on Fargate
    // ========================================

    const frontendTaskDef = new ecs.FargateTaskDefinition(this, "FrontendTaskDef", {
      memoryLimitMiB: 1024,
      cpu: 512,
      family: `${prefix}-Frontend`,
    });

    const frontendContainer = frontendTaskDef.addContainer("frontend", {
      image: ecs.ContainerImage.fromAsset(
        path.join(__dirname, "../../frontend"),
        { file: "Dockerfile", networkMode: ecr_assets.NetworkMode.custom("sagemaker") }
      ),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: `${prefix}-frontend`,
        logRetention: logs.RetentionDays.THREE_DAYS,
      }),
      environment: {
        BACKEND_URL: "http://localhost:8080",
      },
      // ECS コンテナレベルの health check は撤去し、ALB Target Group の health check
      // を唯一の判定源とする（起動さえすれば ALB 判定に委ねる）。二重 health check の
      // 片方を外して stabilize 失敗モードを1つ減らす。ローカルでは起動・/=200 を実証済み。
    });

    frontendContainer.addPortMappings({ containerPort: 3000 });

    const frontendService = new ecs.FargateService(this, "FrontendService", {
      serviceName: `${prefix}-Frontend`,
      cluster,
      taskDefinition: frontendTaskDef,
      desiredCount: 1,
      assignPublicIp: true,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      // 起動直後から ALB health check が即カウント開始して Ready 前に unhealthy 判定
      // → タスク kill → 無限リサイクル、を防ぐ猶予期間。stabilize しない主因の対策。
      healthCheckGracePeriod: cdk.Duration.seconds(180),
    });

    // ========================================
    // ALB: path-based routing
    // ========================================

    const alb = new elbv2.ApplicationLoadBalancer(this, "ALB", {
      loadBalancerName: `MIH-MSYS-Kintai-ALB`,
      vpc,
      internetFacing: true,
    });

    const backendTG = new elbv2.ApplicationTargetGroup(this, "BackendTG", {
      targetGroupName: `MIH-MSYS-Backend-TG`,
      vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
      healthCheck: {
        path: "/api/health",
        interval: cdk.Duration.seconds(30),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
      deregistrationDelay: cdk.Duration.seconds(30),
    });

    const frontendTG = new elbv2.ApplicationTargetGroup(this, "FrontendTG", {
      targetGroupName: `MIH-MSYS-Frontend-TG`,
      vpc,
      port: 3000,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
      // 寛容化: Next.js の初回応答が遅くても unhealthy に倒れにくくする。
      // 200-399 を healthy 扱い（クライアント側リダイレクト前でも / は 200 を返す）。
      healthCheck: {
        path: "/",
        healthyHttpCodes: "200-399",
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(10),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 5,
      },
      deregistrationDelay: cdk.Duration.seconds(30),
    });

    const listener = alb.addListener("Listener", {
      port: 80,
      defaultTargetGroups: [frontendTG],
    });

    listener.addTargetGroups("ApiRouting", {
      targetGroups: [backendTG],
      priority: 10,
      conditions: [elbv2.ListenerCondition.pathPatterns(["/api/*"])],
    });

    backendService.attachToApplicationTargetGroup(backendTG);
    frontendService.attachToApplicationTargetGroup(frontendTG);

    // ========================================
    // Outputs
    // ========================================

    new cdk.CfnOutput(this, "AppURL", {
      value: `http://${alb.loadBalancerDnsName}`,
      description: "Application URL (ALB)",
    });
  }
}
