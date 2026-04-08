package com.esotericsoftware.spine;

import static com.esotericsoftware.spine.SkeletonViewer.dataSuffixes;
import static com.esotericsoftware.spine.SkeletonViewer.endSuffixes;

import java.awt.Toolkit;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.system.Configuration;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.esotericsoftware.spine.SkeletonComparatorDiff.CompareResult;
import com.esotericsoftware.spine.SkeletonComparatorDiff.LoadedSkeletonInfo;

public class SkeletonComparator extends ApplicationAdapter {
	static String[] args;
	static float uiScale = 1;

	private Preferences prefs;
	private SkeletonComparatorUI ui;
	private LoadedSkeletonInfo skeletonA;
	private LoadedSkeletonInfo skeletonB;
	private boolean loadingA;
	private boolean loadingB;
	private boolean reloadingA;
	private boolean reloadingB;
	private PendingLoad pendingLoadA;
	private PendingLoad pendingLoadB;

	public void create () {
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			public void uncaughtException (Thread thread, Throwable ex) {
				System.out.println("Uncaught exception:");
				ex.printStackTrace();
				Runtime.getRuntime().halt(0);
			}
		});

		prefs = Gdx.app.getPreferences("spine-skeletoncomparator");
		ui = new SkeletonComparatorUI(this);

		if (args.length > 0) loadSkeleton(true, Gdx.files.absolute(args[0]));
		if (args.length > 1) loadSkeleton(false, Gdx.files.absolute(args[1]));

		refreshComparison();
	}

	boolean loadSkeleton (boolean loadA, FileHandle skeletonFile) {
		if (skeletonFile == null) return false;

		try {
			LoadedSkeletonInfo loaded = SkeletonComparatorLoader.load(skeletonFile);
			if (loadA) {
				skeletonA = loaded;
			} else {
				skeletonB = loaded;
			}

			ui.toast(t(loadA ? "Loaded A: " : "Loaded B: ", loadA ? "已加载到 A：" : "已加载到 B：") + loaded.file.name());
			refreshComparison();
			return true;
		} catch (Throwable ex) {
			System.out.println("Error loading skeleton: " + skeletonFile.path());
			ex.printStackTrace();
			ui.toast(t("Error loading " + (loadA ? "A" : "B") + ": ", "读取" + (loadA ? "A" : "B") + "失败：") + skeletonFile.name());
			ui.setStatus(t("Load failed for " + (loadA ? "A" : "B") + ".", (loadA ? "A" : "B") + " 读取失败。"));
			return false;
		}
	}

	void reloadSkeleton (boolean reloadA) {
		LoadedSkeletonInfo loaded = reloadA ? skeletonA : skeletonB;
		if (loaded == null) {
			ui.toast(t((reloadA ? "Skeleton A" : "Skeleton B") + " is not loaded.", (reloadA ? "骨骼 A" : "骨骼 B") + " 尚未加载。"));
			return;
		}
		requestLoadSkeleton(reloadA, loaded.file, true);
	}

	void reloadBoth () {
		boolean anyLoaded = false;
		if (skeletonA != null) {
			anyLoaded = true;
			requestLoadSkeleton(true, skeletonA.file, true);
		}
		if (skeletonB != null) {
			anyLoaded = true;
			requestLoadSkeleton(false, skeletonB.file, true);
		}
		if (!anyLoaded) ui.toast(t("No files loaded yet.", "当前还没有加载文件。"));
	}

	void handleDroppedFiles (String[] files) {
		List<FileHandle> validFiles = new ArrayList<FileHandle>(2);
		int ignoredFiles = 0;
		for (String file : files) {
			if (!isSkeletonDataFile(file)) {
				ignoredFiles++;
				continue;
			}
			validFiles.add(Gdx.files.absolute(file));
			if (validFiles.size() == 2) break;
		}
		if (validFiles.isEmpty()) {
			String message = t("No supported skeleton data file found in drop.",
				"拖拽内容中没有找到受支持的 skeleton 数据文件。");
			ui.toast(message);
			ui.setStatus(message);
			return;
		}
		if (ignoredFiles > 0) {
			String ignoredMessage = t(ignoredFiles + " unsupported file(s) were ignored.",
				"已忽略 " + ignoredFiles + " 个不支持的文件。");
			ui.toast(ignoredMessage);
			ui.setStatus(ignoredMessage);
		}

		if (validFiles.size() >= 2) {
			ui.flashDropTargets(true, true);
			requestLoadSkeleton(true, validFiles.get(0), false);
			requestLoadSkeleton(false, validFiles.get(1), false);
			return;
		}

		boolean loadA = ui.preferLoadAForDrop();
		ui.flashDropTargets(loadA, !loadA);
		requestLoadSkeleton(loadA, validFiles.get(0), false);
	}

	public void render () {
		pollPendingLoads();
		Gdx.gl.glClearColor(34 / 255f, 37 / 255f, 43 / 255f, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		ui.render();
	}

	public void resize (int width, int height) {
		ui.resize(width, height);
	}

	public void dispose () {
		ui.dispose();
		super.dispose();
	}

	void refreshComparison () {
		if (skeletonA != null && skeletonB != null) {
			CompareResult result = SkeletonComparatorDiff.compare(skeletonA, skeletonB);
			ui.showCompareResult(result);
		} else
			ui.showWaitingState(skeletonA, skeletonB);
	}

	boolean isChinese () {
		return prefs == null || prefs.getBoolean("languageZh", true);
	}

	void toggleLanguage () {
		boolean chinese = !isChinese();
		prefs.putBoolean("languageZh", chinese);
		prefs.flush();
		ui.refreshLanguage();
		refreshComparison();
	}

	String t (String english, String chinese) {
		return isChinese() ? chinese : english;
	}

	boolean isLoading (boolean loadA) {
		return loadA ? loadingA : loadingB;
	}

	boolean isReloading (boolean loadA) {
		return loadA ? reloadingA : reloadingB;
	}

	void requestLoadSkeleton (final boolean loadA, final FileHandle skeletonFile, boolean reload) {
		if (skeletonFile == null) return;
		if (isLoading(loadA)) return;

		setLoading(loadA, true, reload);
		ui.setStatus(t((reload ? "Reloading " : "Loading ") + (loadA ? "A" : "B") + "...",
			(reload ? "正在刷新 " : "正在读取 ") + (loadA ? "A" : "B") + "..."));

		PendingLoad pendingLoad = new PendingLoad(loadA, skeletonFile);
		if (loadA)
			pendingLoadA = pendingLoad;
		else
			pendingLoadB = pendingLoad;
	}

	private void pollPendingLoads () {
		if (pendingLoadA != null) {
			PendingLoad pendingLoad = pendingLoadA;
			if (pendingLoad.delayFrames > 0)
				pendingLoad.delayFrames--;
			else {
				pendingLoadA = null;
				performLoad(pendingLoad);
			}
		}
		if (pendingLoadB != null) {
			PendingLoad pendingLoad = pendingLoadB;
			if (pendingLoad.delayFrames > 0)
				pendingLoad.delayFrames--;
			else {
				pendingLoadB = null;
				performLoad(pendingLoad);
			}
		}
	}

	private void performLoad (PendingLoad pendingLoad) {
		try {
			loadSkeleton(pendingLoad.loadA, pendingLoad.file);
		} finally {
			setLoading(pendingLoad.loadA, false, false);
		}
	}

	private void setLoading (boolean loadA, boolean loading, boolean reloading) {
		if (loadA) {
			loadingA = loading;
			reloadingA = loading && reloading;
		} else {
			loadingB = loading;
			reloadingB = loading && reloading;
		}
		ui.setLoading(loadA, loading, reloading);
	}

	private static boolean isSkeletonDataFile (String file) {
		for (String endSuffix : endSuffixes) {
			for (String dataSuffix : dataSuffixes) {
				if (file.endsWith(dataSuffix + endSuffix)) return true;
			}
		}
		return false;
	}

	static public void main (String[] args) {
		SkeletonComparator.args = args;

		String os = System.getProperty("os.name");
		float dpiScale = 1;
		if (os.contains("Windows")) dpiScale = Toolkit.getDefaultToolkit().getScreenResolution() / 96f;
		if (os.contains("OS X")) {
			Configuration.GLFW_CHECK_THREAD0.set(Boolean.FALSE);
			Configuration.GLFW_LIBRARY_NAME.set("glfw_async");
			Object object = Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.contentScaleFactor");
			if (object instanceof Float && ((Float)object).intValue() >= 2) dpiScale = 2;
		}
		if (dpiScale >= 2.0f) uiScale = 2;

		final SkeletonComparator comparator = new SkeletonComparator();
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.disableAudio(true);
		config.setWindowedMode((int)(1200 * uiScale), (int)(820 * uiScale));
		config.setTitle("骨骼对比器 / Skeleton Comparator");
		config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 2);
		config.setWindowListener(new Lwjgl3WindowAdapter() {
			@Override
			public void filesDropped (String[] files) {
				comparator.handleDroppedFiles(files);
			}
		});
		new Lwjgl3Application(comparator, config);
	}

	static class PendingLoad {
		final boolean loadA;
		final FileHandle file;
		int delayFrames = 1;

		PendingLoad (boolean loadA, FileHandle file) {
			this.loadA = loadA;
			this.file = file;
		}
	}
}
