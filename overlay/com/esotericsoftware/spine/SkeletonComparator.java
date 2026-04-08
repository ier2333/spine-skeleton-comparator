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
		if (args.length == 0) {
			String lastA = prefs.getString("lastFileA", null);
			String lastB = prefs.getString("lastFileB", null);
			if (lastA != null && lastA.length() > 0) loadSkeleton(true, Gdx.files.absolute(lastA));
			if (lastB != null && lastB.length() > 0) loadSkeleton(false, Gdx.files.absolute(lastB));
		}

		refreshComparison();
	}

	boolean loadSkeleton (boolean loadA, FileHandle skeletonFile) {
		if (skeletonFile == null) return false;

		try {
			LoadedSkeletonInfo loaded = SkeletonComparatorLoader.load(skeletonFile);
			if (loadA) {
				skeletonA = loaded;
				prefs.putString("lastFileA", loaded.file.path());
			} else {
				skeletonB = loaded;
				prefs.putString("lastFileB", loaded.file.path());
			}
			prefs.flush();

			ui.toast((loadA ? "Loaded A: " : "Loaded B: ") + loaded.file.name());
			refreshComparison();
			return true;
		} catch (Throwable ex) {
			System.out.println("Error loading skeleton: " + skeletonFile.path());
			ex.printStackTrace();
			ui.toast("Error loading " + (loadA ? "A" : "B") + ": " + skeletonFile.name());
			ui.setStatus("Load failed for " + (loadA ? "A" : "B") + ".");
			return false;
		}
	}

	void reloadSkeleton (boolean reloadA) {
		LoadedSkeletonInfo loaded = reloadA ? skeletonA : skeletonB;
		if (loaded == null) {
			ui.toast((reloadA ? "Skeleton A" : "Skeleton B") + " is not loaded.");
			return;
		}
		loadSkeleton(reloadA, loaded.file);
	}

	void reloadBoth () {
		boolean anyLoaded = false;
		if (skeletonA != null) {
			anyLoaded = true;
			loadSkeleton(true, skeletonA.file);
		}
		if (skeletonB != null) {
			anyLoaded = true;
			loadSkeleton(false, skeletonB.file);
		}
		if (!anyLoaded) ui.toast("No files loaded yet.");
	}

	void handleDroppedFiles (String[] files) {
		List<FileHandle> validFiles = new ArrayList<FileHandle>(2);
		for (String file : files) {
			if (!isSkeletonDataFile(file)) continue;
			validFiles.add(Gdx.files.absolute(file));
			if (validFiles.size() == 2) break;
		}
		if (validFiles.isEmpty()) {
			ui.toast("No skeleton data file found in drop.");
			return;
		}

		if (validFiles.size() >= 2) {
			loadSkeleton(true, validFiles.get(0));
			loadSkeleton(false, validFiles.get(1));
			return;
		}

		if (skeletonA == null)
			loadSkeleton(true, validFiles.get(0));
		else if (skeletonB == null)
			loadSkeleton(false, validFiles.get(0));
		else
			loadSkeleton(false, validFiles.get(0));
	}

	public void render () {
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

	private void refreshComparison () {
		if (skeletonA != null && skeletonB != null) {
			CompareResult result = SkeletonComparatorDiff.compare(skeletonA, skeletonB);
			ui.showCompareResult(result);
		} else
			ui.showWaitingState(skeletonA, skeletonB);
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
		config.setTitle("Skeleton Comparator");
		config.setBackBufferConfig(8, 8, 8, 8, 24, 0, 2);
		config.setWindowListener(new Lwjgl3WindowAdapter() {
			@Override
			public void filesDropped (String[] files) {
				comparator.handleDroppedFiles(files);
			}
		});
		new Lwjgl3Application(comparator, config);
	}
}
