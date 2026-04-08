package com.esotericsoftware.spine;

import static com.badlogic.gdx.math.Interpolation.fade;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.delay;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeOut;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.removeActor;
import static com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.esotericsoftware.spine.SkeletonComparatorDiff.BoneParentChange;
import com.esotericsoftware.spine.SkeletonComparatorDiff.CompareResult;
import com.esotericsoftware.spine.SkeletonComparatorDiff.LoadedSkeletonInfo;

class SkeletonComparatorUI {
	final SkeletonComparator comparator;

	Stage stage = new Stage(new ScreenViewport());
	com.badlogic.gdx.scenes.scene2d.ui.Skin skin = new com.badlogic.gdx.scenes.scene2d.ui.Skin(
		Gdx.files.internal("skin/skin.json"));

	Window window = new Window("Skeleton Comparator", skin);
	Table root = new Table(skin);
	WidgetGroup toasts = new WidgetGroup();

	TextButton openAButton = new TextButton("Open A", skin);
	TextButton reloadAButton = new TextButton("Reload A", skin);
	TextButton openBButton = new TextButton("Open B", skin);
	TextButton reloadBButton = new TextButton("Reload B", skin);
	TextButton reloadBothButton = new TextButton("Reload Both", skin);

	Label fileALabel = new Label("", skin);
	Label fileBLabel = new Label("", skin);
	Label statusLabel = new Label("", skin);
	Label summaryLabel = new Label("", skin);

	Label animationsOnlyATitle = new Label("", skin);
	Label animationsOnlyBTitle = new Label("", skin);
	Label bonesOnlyATitle = new Label("", skin);
	Label bonesOnlyBTitle = new Label("", skin);
	Label bonesChangedTitle = new Label("", skin);

	Table animationsOnlyATable = new Table(skin);
	Table animationsOnlyBTable = new Table(skin);
	Table bonesOnlyATable = new Table(skin);
	Table bonesOnlyBTable = new Table(skin);
	Table bonesChangedTable = new Table(skin);

	SkeletonComparatorUI (SkeletonComparator comparator) {
		this.comparator = comparator;
		initialize();
		layout();
		events();
	}

	private void initialize () {
		skin.getFont("default").getData().markupEnabled = true;

		window.setMovable(false);
		window.setResizable(false);
		window.setKeepWithinStage(false);

		summaryLabel.setAlignment(Align.topLeft);
		summaryLabel.setWrap(true);
		statusLabel.setAlignment(Align.left);
		statusLabel.setColor(Color.LIGHT_GRAY);

		fileALabel.setAlignment(Align.left);
		fileBLabel.setAlignment(Align.left);
		fileALabel.setWrap(true);
		fileBLabel.setWrap(true);

		animationsOnlyATitle.setColor(Color.WHITE);
		animationsOnlyBTitle.setColor(Color.WHITE);
		bonesOnlyATitle.setColor(Color.WHITE);
		bonesOnlyBTitle.setColor(Color.WHITE);
		bonesChangedTitle.setColor(Color.WHITE);

		configureListTable(animationsOnlyATable);
		configureListTable(animationsOnlyBTable);
		configureListTable(bonesOnlyATable);
		configureListTable(bonesOnlyBTable);
		configureListTable(bonesChangedTable);
	}

	private void layout () {
		root.defaults().pad(8);
		root.top().left();

		root.add(topButtons()).growX().fillX().row();
		root.add(fileSection()).growX().fillX().row();
		root.add(section("Summary", wrap(summaryLabel))).growX().fillX().row();
		root.add(animationSection()).growX().fillX().row();
		root.add(boneSection()).grow().fill().row();
		root.add(statusLabel).growX().fillX().row();

		window.add(root).grow();
		stage.addActor(window);

		Table toastHost = new Table();
		toastHost.setFillParent(true);
		toastHost.top().right().pad(10);
		toastHost.add(toasts).right().top();
		stage.addActor(toastHost);
	}

	private Table topButtons () {
		Table table = new Table(skin);
		table.defaults().padRight(6).left();
		table.add(openAButton);
		table.add(reloadAButton);
		table.add(openBButton);
		table.add(reloadBButton);
		table.add(reloadBothButton);
		return table;
	}

	private Table fileSection () {
		Table table = new Table(skin);
		table.defaults().pad(6).top().left();
		table.add(fileCard("Skeleton A", fileALabel)).growX().fillX().top();
		table.add(fileCard("Skeleton B", fileBLabel)).growX().fillX().top();
		return section("Loaded Files", table);
	}

	private Table animationSection () {
		Table left = listSection(animationsOnlyATitle, animationsOnlyATable);
		Table right = listSection(animationsOnlyBTitle, animationsOnlyBTable);

		Table content = new Table(skin);
		content.defaults().pad(6).top();
		content.add(left).grow().fill();
		content.add(right).grow().fill();
		return section("Animation Differences", content);
	}

	private Table boneSection () {
		Table top = new Table(skin);
		top.defaults().pad(6).top();
		top.add(listSection(bonesOnlyATitle, bonesOnlyATable)).grow().fill();
		top.add(listSection(bonesOnlyBTitle, bonesOnlyBTable)).grow().fill();

		Table content = new Table(skin);
		content.defaults().pad(6).grow().fill();
		content.add(top).growX().fillX().row();
		content.add(listSection(bonesChangedTitle, bonesChangedTable)).grow().fill();
		return section("Bone Differences", content);
	}

	private Table fileCard (String title, Label valueLabel) {
		Table table = new Table(skin);
		table.defaults().left().growX();
		table.add(new Label(title, skin)).row();
		table.add(wrap(valueLabel)).fillX();
		return table;
	}

	private Table listSection (Label titleLabel, Table listTable) {
		Table table = new Table(skin);
		table.defaults().pad(4).left().growX();
		ScrollPane scrollPane = new ScrollPane(listTable, skin, "bg");
		scrollPane.setFadeScrollBars(false);
		table.add(titleLabel).row();
		table.add(scrollPane).grow().fill().minHeight(120);
		return table;
	}

	private Table section (String title, Actor content) {
		Table table = new Table(skin);
		table.defaults().pad(6).left().growX();
		table.add(new Label(title, skin)).row();
		table.add(content).grow().fill();
		return table;
	}

	private Table wrap (Actor actor) {
		Table table = new Table(skin);
		table.add(actor).growX().fillX().left();
		return table;
	}

	private void configureListTable (Table table) {
		table.top().left();
		table.defaults().left().pad(2);
	}

	private void events () {
		openAButton.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				FileHandle file = chooseSkeletonFile();
				if (file != null) comparator.loadSkeleton(true, file);
			}
		});
		reloadAButton.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				comparator.reloadSkeleton(true);
			}
		});
		openBButton.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				FileHandle file = chooseSkeletonFile();
				if (file != null) comparator.loadSkeleton(false, file);
			}
		});
		reloadBButton.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				comparator.reloadSkeleton(false);
			}
		});
		reloadBothButton.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				comparator.reloadBoth();
			}
		});

		Gdx.input.setInputProcessor(stage);
	}

	private FileHandle chooseSkeletonFile () {
		FileDialog fileDialog = new FileDialog((Frame)null, "Choose skeleton file");
		fileDialog.setMode(FileDialog.LOAD);
		fileDialog.setVisible(true);
		String name = fileDialog.getFile();
		String dir = fileDialog.getDirectory();
		if (name == null || dir == null) return null;
		return new FileHandle(new File(dir, name).getAbsolutePath());
	}

	void setLoadedFiles (LoadedSkeletonInfo skeletonA, LoadedSkeletonInfo skeletonB) {
		fileALabel.setText(formatFileInfo(skeletonA, "Waiting for file A"));
		fileBLabel.setText(formatFileInfo(skeletonB, "Waiting for file B"));
	}

	void showWaitingState (LoadedSkeletonInfo skeletonA, LoadedSkeletonInfo skeletonB) {
		setLoadedFiles(skeletonA, skeletonB);
		setStatus("Drop two skeleton files or use Open A / Open B.");
		if (skeletonA == null && skeletonB == null)
			summaryLabel.setText("Load two Spine skeleton files to compare animations and bones.");
		else if (skeletonA == null)
			summaryLabel.setText("Skeleton B is loaded. Waiting for Skeleton A.");
		else
			summaryLabel.setText("Skeleton A is loaded. Waiting for Skeleton B.");

		setTitle(animationsOnlyATitle, "A only", 0);
		setTitle(animationsOnlyBTitle, "B only", 0);
		setTitle(bonesOnlyATitle, "A only", 0);
		setTitle(bonesOnlyBTitle, "B only", 0);
		setTitle(bonesChangedTitle, "Parent changed", 0);
		fillLines(animationsOnlyATable, null);
		fillLines(animationsOnlyBTable, null);
		fillLines(bonesOnlyATable, null);
		fillLines(bonesOnlyBTable, null);
		fillLines(bonesChangedTable, null);
	}

	void showCompareResult (CompareResult result) {
		setLoadedFiles(result.skeletonA, result.skeletonB);
		summaryLabel.setText(result.summaryText());
		setStatus("Comparison updated.");

		setTitle(animationsOnlyATitle, "Animations only in A", result.animationsOnlyInA.size());
		setTitle(animationsOnlyBTitle, "Animations only in B", result.animationsOnlyInB.size());
		setTitle(bonesOnlyATitle, "Bones only in A", result.bonesOnlyInA.size());
		setTitle(bonesOnlyBTitle, "Bones only in B", result.bonesOnlyInB.size());
		setTitle(bonesChangedTitle, "Same bone, different parent", result.bonesParentChanged.size());

		fillLines(animationsOnlyATable, result.animationsOnlyInA);
		fillLines(animationsOnlyBTable, result.animationsOnlyInB);
		fillLines(bonesOnlyATable, result.bonesOnlyInA);
		fillLines(bonesOnlyBTable, result.bonesOnlyInB);
		fillBoneChanges(bonesChangedTable, result.bonesParentChanged);
	}

	void setStatus (String text) {
		statusLabel.setText(text);
	}

	void toast (String text) {
		Table table = new Table(skin);
		table.add(new Label(text, skin));
		table.getColor().a = 0;
		table.pack();
		table.setPosition(-table.getWidth(), -3 - table.getHeight());
		table.addAction(sequence(parallel(moveBy(0, table.getHeight(), 0.25f, fade), fadeIn(0.25f)),
			delay(3.5f), parallel(moveBy(0, table.getHeight(), 0.25f, fade), fadeOut(0.25f)), removeActor()));
		for (Actor actor : toasts.getChildren())
			actor.addAction(moveBy(0, table.getHeight(), 0.25f, fade));
		toasts.addActor(table);
	}

	void render () {
		stage.act();
		stage.draw();
	}

	void resize (int width, int height) {
		stage.getViewport().update(width, height, true);
		window.setBounds(0, 0, width, height);
	}

	void dispose () {
		stage.dispose();
		skin.dispose();
	}

	private void fillLines (Table table, List<String> lines) {
		table.clearChildren();
		if (lines == null || lines.isEmpty()) {
			table.add(new Label("None", skin)).left().row();
			return;
		}
		for (String line : lines)
			table.add(new Label(line, skin)).left().row();
	}

	private void fillBoneChanges (Table table, List<BoneParentChange> changes) {
		table.clearChildren();
		if (changes == null || changes.isEmpty()) {
			table.add(new Label("None", skin)).left().row();
			return;
		}
		for (BoneParentChange change : changes) {
			String line = change.boneName + " : A=" + formatParent(change.parentA) + ", B=" + formatParent(change.parentB);
			table.add(new Label(line, skin)).left().row();
		}
	}

	private void setTitle (Label label, String title, int count) {
		label.setText(title + " (" + count + ")");
	}

	private String formatParent (String parentName) {
		return parentName == null ? "<root>" : parentName;
	}

	private String formatFileInfo (LoadedSkeletonInfo skeletonInfo, String fallback) {
		if (skeletonInfo == null) return fallback;
		StringBuilder text = new StringBuilder(160);
		text.append(skeletonInfo.file.name());
		text.append('\n');
		text.append(skeletonInfo.file.path());
		text.append('\n');
		text.append("Animations: ");
		text.append(skeletonInfo.animationNames.size());
		text.append(" | Bones: ");
		text.append(skeletonInfo.boneParents.size());
		return text.toString();
	}
}
