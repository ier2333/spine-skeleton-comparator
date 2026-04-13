# spine-skeleton-comparator

`spine-skeleton-comparator` 是一个运行在浏览器中的 Spine 对比 / 预览工具，适合直接部署到 Cloudflare Pages。

## 项目定位

- 运行在浏览器中
- 不内置用户的 Spine 动画资源
- 用户在页面里手动选择本地 `.skel` / `.skel.bytes` / `.json`、`.atlas` / `.atlas.txt` 和贴图文件
- 适合部署为公开可访问的在线工具

## 已包含内容

- Web 页面源码
- Vite 构建配置
- 工具按钮图标资源

## 不包含内容

以下内容不建议放进公开仓库或部署产物：

- 商业或测试用 Spine skeleton 文件
- atlas / 贴图示例资源
- 本地测试目录
- `node_modules/`
- `dist/`

## 本地开发

安装依赖：

```bash
npm install
```

启动开发服务器：

```bash
npm run dev
```

生产构建：

```bash
npm run build
```

构建结果输出到 `dist/`。

## Cloudflare Pages 部署建议

推荐直接把当前仓库连接到 Cloudflare 的 Git 自动构建。

建议配置：

- Framework preset: `Vite`
- Build command: `npm run build`
- Output directory: `dist`
- Root directory: `/`

如果 Cloudflare 后台已经统一到 Workers 构建流，仓库根目录中已包含 `wrangler.jsonc`，可直接使用默认部署命令 `npx wrangler deploy`。

当前项目已经配置为相对路径构建，适合直接部署，也更方便将来挂到子目录下。

## 部署前检查

上线前建议确认：

1. `dist/` 中不包含任何测试 Spine 动画资源
2. 页面里只保留通用图标资源
3. README 中没有个人路径、局域网地址或测试目录说明
4. Cloudflare 项目绑定的是当前仓库根目录
