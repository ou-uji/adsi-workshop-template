FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --legacy-peer-deps || npm install

COPY . .

ENV DEPLOY=1
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:20-alpine AS runner

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

# Next.js standalone は monorepo のパス構造を保持するため、
# 出力は .next/standalone 直下ではなく .next/standalone/app/ に生成される
# （turbopack.root が / に解決され app ディレクトリ階層が1段残るため）。
# その app/ の中身を丸ごと /app に展開し、static / public を整合する位置へ置く。
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone/app ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static
COPY --from=build /app/public ./public

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]
