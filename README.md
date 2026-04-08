# Spine Skeleton Comparator

`SkeletonComparator` 是一个基于 `Spine SkeletonViewer` 思路扩展出来的小工具，用于对比两份 Spine skeleton 文件在动画和骨骼结构上的差异。

它当前重点解决的是“快速看出两个导出文件哪里不同”，而不是逐帧渲染比对。

## 当前能力

- 读取两份 Spine skeleton 文件
- 支持 `.json`、`.skel` 以及 `SkeletonViewer` 兼容的常见后缀
- 对比动画数量和动画名差异
- 对比骨骼数量和骨骼名差异
- 对比同名骨骼的父骨骼差异
- 提供独立窗口 UI
- 支持左右拖拽区域
- 支持点击 A / B 卡片直接选择文件
- 支持单独刷新 A / B
- 支持中英文切换

## 适用场景

- 检查两个 Spine 导出结果是否一致
- 快速确认动画是否缺失或多出
- 对比骨骼结构是否发生变化
- 辅助资源版本回归检查

## 仓库结构

这个仓库是比较器功能的独立整理目录，方便继续开发、迁移和同步到上游工程。

- `overlay/`
  - 当前 4 个核心类的独立副本
- `upstream-overlay/`
  - 当前真正运行所依赖的上游覆盖文件
  - 包含 `SkeletonViewerAtlas.java`、`build.gradle`、`skin/` 资源和中文字体
- `scripts/`
  - 用于把本仓库中的覆盖文件同步到上游 `spine-libgdx` 并直接运行
- `UPSTREAM_CHANGES.md`
  - 记录为了接入上游 `spine-runtimes` 做过的修改

## 核心类

当前核心实现包括：

- `overlay/com/esotericsoftware/spine/SkeletonComparator.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorUI.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorLoader.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorDiff.java`

## 与上游工程的关系

当前可运行版本仍然是通过接入上游 `spine-runtimes` 工程实现的。

- `overlay/` 保存比较器本体
- `upstream-overlay/` 保存当前版本真正需要同步到上游的其他文件
- 上游改动说明见 `UPSTREAM_CHANGES.md`

## 快速运行

### macOS / Linux

默认假设上游工程位于同级目录的 `../spine-runtimes/spine-libgdx`：

```bash
./scripts/run-comparator.sh
```

如果你的上游工程不在这个位置，可以先设置环境变量：

```bash
SPINE_LIBGDX_DIR="/path/to/spine-libgdx" ./scripts/run-comparator.sh
```

如果要带两份文件启动：

```bash
./scripts/run-comparator.sh "/path/to/A.json" "/path/to/B.json"
```

### Windows

默认假设上游工程位于同级目录的 `..\spine-runtimes\spine-libgdx`：

```bat
scripts\run-comparator.bat
```

如果你的上游工程在其他位置，可以先设置环境变量：

```bat
set SPINE_LIBGDX_DIR=D:\path\to\spine-libgdx
scripts\run-comparator.bat
```

如果要带两份文件启动：

```bat
scripts\run-comparator.bat "D:\path\A.json" "D:\path\B.json"
```

## 当前状态

- UI 已可运行
- 核心 diff 逻辑已完成
- 已验证真实示例文件的结构对比结果
- 目前重点仍是“结构差异”，还不是完整的动画深度比较器
- 当前阶段优先保证开发调试方便，最终分发包可后续再做

## 下一步建议

适合继续扩展的方向：

- 同名动画时长差异
- timeline 数量差异
- 更细的属性级差异
- 根据 A / B 差异生成合并候选文件 C
- Windows 侧正式打包为更易分发的启动方式
