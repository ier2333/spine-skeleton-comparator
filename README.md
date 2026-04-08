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
- 支持 `Open A`、`Open B`、`Reload A`、`Reload B`、`Reload Both`
- 支持拖拽文件到窗口

## 适用场景

- 检查两个 Spine 导出结果是否一致
- 快速确认动画是否缺失或多出
- 对比骨骼结构是否发生变化
- 辅助资源版本回归检查

## 当前仓库内容

这个仓库目前是一个“开发整理目录”，主要用于把当前成果独立整理出来，方便继续开发和迁移。

### 目录说明

- `overlay/`
  - 保存当前 4 个核心类的独立副本
- `UPSTREAM_CHANGES.md`
  - 记录为了接入上游 `spine-runtimes` 做过的额外修改

## 核心类

当前核心实现包括：

- `overlay/com/esotericsoftware/spine/SkeletonComparator.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorUI.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorLoader.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorDiff.java`

## 当前真实运行位置

当前已经接入并可运行的版本，实际位于上游工程：

- `spine-runtimes/spine-libgdx/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonComparator.java`
- `spine-runtimes/spine-libgdx/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonComparatorUI.java`
- `spine-runtimes/spine-libgdx/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonComparatorLoader.java`
- `spine-runtimes/spine-libgdx/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonComparatorDiff.java`

此外还涉及这些上游集成修改：

- `spine-runtimes/spine-libgdx/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonViewerAtlas.java`
- `spine-runtimes/spine-libgdx/spine-skeletonviewer/src/com/esotericsoftware/spine/SkeletonViewer.java`
- `spine-runtimes/spine-libgdx/build.gradle`

详细说明见 `UPSTREAM_CHANGES.md`。

## 当前启动方式

在上游工程根目录执行：

```powershell
cd F:\Coding\spine_Ani\spine-runtimes\spine-libgdx
java -jar .\spine-skeletonviewer\build\libs\spine-skeletonviewer-4.2.13-SNAPSHOT-comparator.jar
```

如果要带两份文件启动：

```powershell
cd F:\Coding\spine_Ani\spine-runtimes\spine-libgdx
java -jar .\spine-skeletonviewer\build\libs\spine-skeletonviewer-4.2.13-SNAPSHOT-comparator.jar "F:\path\A.json" "F:\path\B.json"
```

## 当前状态

- UI 已可运行
- 核心 diff 逻辑已完成
- 已验证真实示例文件的结构对比结果
- 目前重点仍是“结构差异”，还不是完整的动画深度比较器

## 下一步建议

适合继续扩展的方向：

- 同名动画时长差异
- timeline 数量差异
- 更细的属性级差异
- 更清晰的分组展示和颜色标识
- 打包为更易分发的启动方式
