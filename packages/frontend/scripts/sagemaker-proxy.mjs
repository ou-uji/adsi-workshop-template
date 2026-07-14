// SageMaker Code Editor 復元プロキシ（.claude/skills/sagemaker-code-editor/SKILL.md）。
//
// 構成:
//   ブラウザ → ゲートウェイ → code-server が "/codeeditor/default" を剥がす
//     → absports は剥がさず :3000（このプロキシ）に "/absports/3000/..." が届く
//       → "/codeeditor/default" を前置して next(:3001) へ転送
//
// next 側 basePath はフルパス（/codeeditor/default/absports/3000）なので、
// next が返す Location / アセット URL は正しいフルパスになる。ここでは受信リクエストの
// パスにフル basePath を復元して転送するだけ。
import http from "node:http";

const LISTEN_PORT = Number(process.env.PROXY_PORT ?? 3000);
const NEXT_PORT = Number(process.env.NEXT_PORT ?? 3001);
const NEXT_HOST = process.env.NEXT_HOST ?? "127.0.0.1";
// code-server が剥がす接頭辞。受信時にこれを前置してフル basePath を復元する。
// 受信パスは既に "/absports/3000/..." を含むので、ここでは "/codeeditor/default" のみ前置する。
const RESTORE_PREFIX = process.env.RESTORE_PREFIX ?? "/codeeditor/default";
// next 側のフル basePath（二重前置の判定に使う）。
const FULL_BASE_PATH =
  process.env.NEXT_PUBLIC_BASE_PATH ?? "/codeeditor/default/absports/3000";

const server = http.createServer((req, res) => {
  // 既にフル basePath 付きならそのまま。そうでなければ /codeeditor/default を前置して復元。
  const alreadyPrefixed = req.url.startsWith(FULL_BASE_PATH);
  const targetPath = alreadyPrefixed ? req.url : `${RESTORE_PREFIX}${req.url}`;

  const proxyReq = http.request(
    {
      host: NEXT_HOST,
      port: NEXT_PORT,
      method: req.method,
      path: targetPath,
      headers: { ...req.headers, host: `${NEXT_HOST}:${NEXT_PORT}` },
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers);
      proxyRes.pipe(res, { end: true });
    },
  );

  proxyReq.on("error", (err) => {
    res.writeHead(502, { "Content-Type": "text/plain; charset=utf-8" });
    res.end(`proxy error: ${err.message}`);
  });

  req.pipe(proxyReq, { end: true });
});

server.listen(LISTEN_PORT, () => {
  console.log(
    `[sagemaker-proxy] :${LISTEN_PORT} -> ${NEXT_HOST}:${NEXT_PORT} (restore "${RESTORE_PREFIX}")`,
  );
});
