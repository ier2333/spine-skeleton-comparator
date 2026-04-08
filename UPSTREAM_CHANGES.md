# Upstream Changes

这个文件记录了为了让 `SkeletonComparator` 在上游 `spine-runtimes` 中运行，额外做过的改动。

## 1. `SkeletonViewerAtlas.java`

目的：

- 让 atlas 加载器不再强依赖 `SkeletonViewer`
- 使其既能服务原 `SkeletonViewer`，也能服务新建的 `SkeletonComparator`

核心变化：

- 增加了 `Listener` 接口
- 增加了不依赖 `SkeletonViewer` 的构造函数
- atlas 设置和错误回调改为通过 `Listener` 转发
- 查找独立图片时，改为使用当前传入的 `skeletonFile`

## 2. `SkeletonViewer.java`

目的：

- 修复编译错误

核心变化：

- 删除了错误的 `com.badlogic.gdx.utils.StringBuilder` import
- 保留使用 JDK 自带的 `StringBuilder`

## 3. `build.gradle`

目的：

- 为比较器生成单独可启动的 jar

核心变化：

- 在 `project("spine-skeletonviewer")` 中新增 `comparatorJar` 任务
- `Main-Class` 指向 `com.esotericsoftware.spine.SkeletonComparator`
- `build` 任务增加对 `comparatorJar` 的依赖

## 4. 当前新增类

新增的 4 个核心类已经放在：

- `overlay/com/esotericsoftware/spine/SkeletonComparator.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorUI.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorLoader.java`
- `overlay/com/esotericsoftware/spine/SkeletonComparatorDiff.java`

## 5. 回家后继续开发时建议先检查

1. 你的本地 JDK 是否可用
2. Gradle wrapper 是否能联网或已缓存
3. 上游目录结构是否与当前版本一致
4. `SkeletonViewerAtlas.java` 的集成修改是否已同步
