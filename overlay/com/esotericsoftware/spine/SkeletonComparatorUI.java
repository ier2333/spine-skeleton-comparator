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
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.esotericsoftware.spine.SkeletonComparatorDiff.BoneParentChange;
import com.esotericsoftware.spine.SkeletonComparatorDiff.CompareResult;
import com.esotericsoftware.spine.SkeletonComparatorDiff.LoadedSkeletonInfo;

class SkeletonComparatorUI {
	final SkeletonComparator comparator;

	Stage stage = new Stage(new ScreenViewport());
	com.badlogic.gdx.scenes.scene2d.ui.Skin skin = new com.badlogic.gdx.scenes.scene2d.ui.Skin(
		Gdx.files.internal("skin/skin.json"));
	FreeTypeFontGenerator fontGenerator;
	BitmapFont generatedFont;

	Window window = new Window("Skeleton Comparator", skin);
	Table root = new Table(skin);
	WidgetGroup toasts = new WidgetGroup();

	TextButton reloadAButton = new TextButton("Reload A", skin);
	TextButton reloadBButton = new TextButton("Reload B", skin);
	TextButton languageButton = new TextButton("CH / EN", skin);

	Label loadedFilesSectionTitle = new Label("", skin, "title");
	Label summarySectionTitle = new Label("", skin, "title");
	Label animationSectionTitle = new Label("", skin, "title");
	Label boneSectionTitle = new Label("", skin, "title");

	Label fileATitleLabel = new Label("", skin, "title");
	Label fileAHintLabel = new Label("", skin);
	Label fileBTitleLabel = new Label("", skin, "title");
	Label fileBHintLabel = new Label("", skin);

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
	Table fileACard = new Table(skin);
	Table fileBCard = new Table(skin);
	Table boneContent = new Table(skin);
	Table boneTop = new Table(skin);
	Table bonesChangedSection = new Table(skin);
	Table summarySectionCard;
	Table animationSectionCard;
	Table boneSectionCard;

	long flashAUntilMs;
	long flashBUntilMs;
	long flashAnimationUntilMs;
	long flashBoneUntilMs;
	boolean lastHighlightA;
	boolean lastHighlightB;
	boolean lastHighlightAnimation;
	boolean lastHighlightBone;

	static final Color DROP_BASE_A = new Color(0.15f, 0.24f, 0.33f, 0.95f);
	static final Color DROP_BASE_B = new Color(0.15f, 0.30f, 0.23f, 0.95f);
	static final Color DROP_HOVER_A = new Color(0.20f, 0.42f, 0.62f, 1f);
	static final Color DROP_HOVER_B = new Color(0.18f, 0.52f, 0.36f, 1f);
	static final Color DROP_FLASH_A = new Color(0.23f, 0.52f, 0.86f, 1f);
	static final Color DROP_FLASH_B = new Color(0.22f, 0.68f, 0.43f, 1f);
	static final Color SECTION_BASE = new Color(0.12f, 0.13f, 0.16f, 0.92f);
	static final Color SECTION_FLASH = new Color(0.45f, 0.37f, 0.10f, 0.95f);

	SkeletonComparatorUI (SkeletonComparator comparator) {
		this.comparator = comparator;
		initialize();
		layout();
		events();
		refreshLanguage();
	}

	private void initialize () {
		installChineseCapableFont();
		applyWidgetStyles();
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
		fileAHintLabel.setWrap(true);
		fileBHintLabel.setWrap(true);
		fileAHintLabel.setColor(0.82f, 0.90f, 1f, 1f);
		fileBHintLabel.setColor(0.83f, 1f, 0.88f, 1f);
		reloadAButton.padLeft(8).padRight(8);
		reloadBButton.padLeft(8).padRight(8);

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
		applyDropTargetStyles(false, false);
	}

	private void layout () {
		root.defaults().pad(8);
		root.top().left();

		root.add(fileSection()).growX().fillX().row();
		summarySectionCard = section(summarySectionTitle, wrap(summaryLabel));
		animationSectionCard = section(animationSectionTitle, animationSection());
		boneSectionCard = section(boneSectionTitle, boneSection());
		root.add(summarySectionCard).growX().fillX().row();
		root.add(animationSectionCard).growX().fillX().row();
		root.add(boneSectionCard).grow().fill().row();
		root.add(statusLabel).growX().fillX().row();

		window.add(root).grow();
		stage.addActor(window);

		Table toastHost = new Table();
		toastHost.setFillParent(true);
		toastHost.top().right().pad(10);
		toastHost.add(toasts).right().top();
		stage.addActor(toastHost);
	}

	private Table fileSection () {
		Table table = new Table(skin);
		table.defaults().pad(6).top().left();
		table.add(fileSectionHeader()).growX().fillX().colspan(2).row();
		fileACard = fileCard(fileATitleLabel, fileAHintLabel, fileALabel);
		fileBCard = fileCard(fileBTitleLabel, fileBHintLabel, fileBLabel);
		table.add(fileACard).growX().fillX().top();
		table.add(fileBCard).growX().fillX().top();
		return table;
	}

	private Table fileSectionHeader () {
		Table header = new Table(skin);
		header.defaults().pad(6).left();
		header.add(loadedFilesSectionTitle).left();
		header.add().growX();
		header.add(languageButton).width(78).right().padRight(6);
		return header;
	}

	private Table boneSection () {
		boneTop.defaults().pad(6).top();
		boneTop.add(listSection(bonesOnlyATitle, bonesOnlyATable)).grow().fill();
		boneTop.add(listSection(bonesOnlyBTitle, bonesOnlyBTable)).grow().fill();
		bonesChangedSection = listSection(bonesChangedTitle, bonesChangedTable);
		boneContent.defaults().pad(6).grow().fill();
		rebuildBoneSection(false);
		return boneContent;
	}

	private Table animationSection () {
		Table left = listSection(animationsOnlyATitle, animationsOnlyATable);
		Table right = listSection(animationsOnlyBTitle, animationsOnlyBTable);

		Table content = new Table(skin);
		content.defaults().pad(6).top();
		content.add(left).grow().fill();
		content.add(right).grow().fill();
		return content;
	}

	private Table fileCard (Label titleLabel, Label hintLabel, Label valueLabel) {
		Table table = new Table(skin);
		table.defaults().left().growX();
		table.pad(12);
		Table header = new Table(skin);
		header.defaults().left().padBottom(4);
		header.add(titleLabel).left();
		header.add().growX();
		header.add(titleLabel == fileATitleLabel ? createReloadControl(true) : createReloadControl(false)).right().width(96);
		table.add(header).growX().fillX().row();
		table.add(hintLabel).fillX().padTop(4).row();
		table.add(wrap(valueLabel)).fillX().padTop(8);
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

	private Table section (Label titleLabel, Actor content) {
		Table table = new Table(skin);
		table.defaults().pad(6).left().growX();
		table.pad(6);
		table.add(titleLabel).row();
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
		fileACard.addListener(new ClickListener() {
			public void clicked (InputEvent event, float x, float y) {
				if (event.getTarget() != null && event.getTarget().isDescendantOf(reloadAButton)) return;
				FileHandle file = chooseSkeletonFile();
				if (file != null) comparator.requestLoadSkeleton(true, file, false);
			}
		});
		reloadAButton.addListener(new ClickListener() {
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				event.stop();
				return super.touchDown(event, x, y, pointer, button);
			}

			public void clicked (InputEvent event, float x, float y) {
				event.stop();
				comparator.reloadSkeleton(true);
			}
		});
		fileBCard.addListener(new ClickListener() {
			public void clicked (InputEvent event, float x, float y) {
				if (event.getTarget() != null && event.getTarget().isDescendantOf(reloadBButton)) return;
				FileHandle file = chooseSkeletonFile();
				if (file != null) comparator.requestLoadSkeleton(false, file, false);
			}
		});
		reloadBButton.addListener(new ClickListener() {
			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				event.stop();
				return super.touchDown(event, x, y, pointer, button);
			}

			public void clicked (InputEvent event, float x, float y) {
				event.stop();
				comparator.reloadSkeleton(false);
			}
		});
		languageButton.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				comparator.toggleLanguage();
			}
		});

		Gdx.input.setInputProcessor(stage);
	}

	private FileHandle chooseSkeletonFile () {
		FileDialog fileDialog = new FileDialog((Frame)null, comparator.t("Choose skeleton file", "选择 skeleton 文件"));
		fileDialog.setMode(FileDialog.LOAD);
		fileDialog.setVisible(true);
		String name = fileDialog.getFile();
		String dir = fileDialog.getDirectory();
		if (name == null || dir == null) return null;
		return new FileHandle(new File(dir, name).getAbsolutePath());
	}

	void setLoadedFiles (LoadedSkeletonInfo skeletonA, LoadedSkeletonInfo skeletonB) {
		fileALabel.setText(formatFileInfo(skeletonA, comparator.t("Waiting for file A", "等待文件 A")));
		fileBLabel.setText(formatFileInfo(skeletonB, comparator.t("Waiting for file B", "等待文件 B")));
	}

	void showWaitingState (LoadedSkeletonInfo skeletonA, LoadedSkeletonInfo skeletonB) {
		setLoadedFiles(skeletonA, skeletonB);
		setStatus(comparator.t("Drop files into the left or right area, or click either card to browse.",
			"将文件拖到左侧或右侧区域，或直接点击任一卡片选择文件。"));
		if (skeletonA == null && skeletonB == null)
			summaryLabel.setText(comparator.t("Load two Spine skeleton files to compare animations and bones.",
				"加载两个 Spine skeleton 文件后，即可比较动画和骨骼。"));
		else if (skeletonA == null)
			summaryLabel.setText(comparator.t("Skeleton B is loaded. Waiting for Skeleton A.",
				"骨骼 B 已加载，等待骨骼 A。"));
		else
			summaryLabel.setText(comparator.t("Skeleton A is loaded. Waiting for Skeleton B.",
				"骨骼 A 已加载，等待骨骼 B。"));

		setTitle(animationsOnlyATitle, "A only", "仅 A 包含", 0);
		setTitle(animationsOnlyBTitle, "B only", "仅 B 包含", 0);
		setTitle(bonesOnlyATitle, "A only", "仅 A 包含", 0);
		setTitle(bonesOnlyBTitle, "B only", "仅 B 包含", 0);
		setTitle(bonesChangedTitle, "Parent changed", "父级变化", 0);
		fillLines(animationsOnlyATable, null);
		fillLines(animationsOnlyBTable, null);
		fillLines(bonesOnlyATable, null);
		fillLines(bonesOnlyBTable, null);
		fillLines(bonesChangedTable, null);
		rebuildBoneSection(false);
		flashDifferenceSections(false, false);
	}

	void showCompareResult (CompareResult result) {
		setLoadedFiles(result.skeletonA, result.skeletonB);
		summaryLabel.setText(result.summaryText(comparator.isChinese()));
		setStatus(comparator.t("Comparison updated. Drop to the left or right area to replace A or B.",
			"对比结果已更新。拖到左侧或右侧区域可替换 A 或 B。"));

		setTitle(animationsOnlyATitle, "Animations only in A", "仅 A 的动画", result.animationsOnlyInA.size());
		setTitle(animationsOnlyBTitle, "Animations only in B", "仅 B 的动画", result.animationsOnlyInB.size());
		setTitle(bonesOnlyATitle, "Bones only in A", "仅 A 的骨骼", result.bonesOnlyInA.size());
		setTitle(bonesOnlyBTitle, "Bones only in B", "仅 B 的骨骼", result.bonesOnlyInB.size());
		setTitle(bonesChangedTitle, "Same bone, different parent", "同名骨骼父级不同", result.bonesParentChanged.size());

		fillLines(animationsOnlyATable, result.animationsOnlyInA);
		fillLines(animationsOnlyBTable, result.animationsOnlyInB);
		fillLines(bonesOnlyATable, result.bonesOnlyInA);
		fillLines(bonesOnlyBTable, result.bonesOnlyInB);
		fillBoneChanges(bonesChangedTable, result.bonesParentChanged);
		rebuildBoneSection(!result.bonesParentChanged.isEmpty());
		flashDifferenceSections(!result.animationsOnlyInA.isEmpty() || !result.animationsOnlyInB.isEmpty(),
			!result.bonesOnlyInA.isEmpty() || !result.bonesOnlyInB.isEmpty() || !result.bonesParentChanged.isEmpty());
	}

	void setStatus (String text) {
		statusLabel.setText(text);
	}

	void refreshLanguage () {
		window.getTitleLabel().setText(comparator.t("Skeleton Comparator", "骨骼对比器"));
		reloadAButton.setText(loadingText(true));
		reloadBButton.setText(loadingText(false));
		languageButton.setText(comparator.isChinese() ? "CH / EN" : "EN / CH");

		loadedFilesSectionTitle.setText(comparator.t("Loaded Files", "已加载文件"));
		summarySectionTitle.setText(comparator.t("Summary", "摘要"));
		animationSectionTitle.setText(comparator.t("Animation Differences", "动画差异"));
		boneSectionTitle.setText(comparator.t("Bone Differences", "骨骼差异"));

		fileATitleLabel.setText(comparator.t("Skeleton A", "骨骼 A"));
		fileBTitleLabel.setText(comparator.t("Skeleton B", "骨骼 B"));
		fileAHintLabel.setText(comparator.t("Drop here to load as A, or click to browse.\nDrop target: A",
			"可拖拽至此，作为 A 读取，或点击选择文件。"));
		fileBHintLabel.setText(comparator.t("Drop here to load as B, or click to browse.\nDrop target: B",
			"可拖拽至此，作为 B 读取，或点击选择文件。"));
	}

	boolean preferLoadAForDrop () {
		Vector2 stagePoint = stage.screenToStageCoordinates(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
		float midpoint = stage.getViewport().getWorldWidth() / 2f;
		return stagePoint.x <= midpoint;
	}

	void flashDropTargets (boolean highlightA, boolean highlightB) {
		long flashUntil = System.currentTimeMillis() + 900;
		if (highlightA) flashAUntilMs = flashUntil;
		if (highlightB) flashBUntilMs = flashUntil;
		applyDropTargetStyles(isFlashingA(), isFlashingB());
	}

	void toast (String text) {
		Table table = new Table(skin);
		table.setBackground(skin.newDrawable("white", new Color(0.10f, 0.10f, 0.10f, 0.92f)));
		table.pad(10);
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
		boolean hoverA = isPointerOver(fileACard);
		boolean hoverB = isPointerOver(fileBCard);
		applyDropTargetStyles(hoverA || isFlashingA(), hoverB || isFlashingB());
		applyDifferenceSectionStyles(isFlashingAnimation(), isFlashingBone());
		stage.act();
		stage.draw();
	}

	void setLoading (boolean loadA, boolean loading, boolean reloading) {
		TextButton button = loadA ? reloadAButton : reloadBButton;
		button.setDisabled(loading);
		button.setText(loading ? (reloading ? comparator.t("Reloading", "刷新中") : comparator.t("Loading", "加载中"))
			: comparator.t("Reload", "刷新"));
	}

	void resize (int width, int height) {
		stage.getViewport().update(width, height, true);
		window.setBounds(0, 0, width, height);
	}

	void dispose () {
		stage.dispose();
		if (generatedFont != null) generatedFont.dispose();
		if (fontGenerator != null) fontGenerator.dispose();
		skin.dispose();
	}

	private void fillLines (Table table, List<String> lines) {
		table.clearChildren();
		if (lines == null || lines.isEmpty()) {
			table.add(new Label(comparator.t("None", "无"), skin)).left().row();
			return;
		}
		for (String line : lines)
			table.add(new Label(line, skin)).left().row();
	}

	private void fillBoneChanges (Table table, List<BoneParentChange> changes) {
		table.clearChildren();
		if (changes == null || changes.isEmpty()) {
			table.add(new Label(comparator.t("None", "无"), skin)).left().row();
			return;
		}
		for (BoneParentChange change : changes) {
			String line = change.boneName + " : A=" + formatParent(change.parentA) + ", B=" + formatParent(change.parentB);
			table.add(new Label(line, skin)).left().row();
		}
	}

	private void setTitle (Label label, String english, String chinese, int count) {
		label.setText(comparator.t(english, chinese) + " (" + count + ")");
	}

	private String formatParent (String parentName) {
		return parentName == null ? comparator.t("<root>", "<根骨骼>") : parentName;
	}

	private String formatFileInfo (LoadedSkeletonInfo skeletonInfo, String fallback) {
		if (skeletonInfo == null) return fallback;
		StringBuilder text = new StringBuilder(160);
		text.append(skeletonInfo.file.name());
		text.append('\n');
		text.append(skeletonInfo.file.path());
		text.append('\n');
		text.append(comparator.t("Animations: ", "动画: "));
		text.append(skeletonInfo.animationNames.size());
		text.append(comparator.t(" | Bones: ", " | 骨骼: "));
		text.append(skeletonInfo.boneParents.size());
		return text.toString();
	}

	private void rebuildBoneSection (boolean showParentChanges) {
		boneContent.clearChildren();
		boneContent.add(boneTop).growX().fillX().row();
		if (showParentChanges) boneContent.add(bonesChangedSection).grow().fill();
	}

	private boolean isFlashingA () {
		return System.currentTimeMillis() < flashAUntilMs;
	}

	private boolean isFlashingB () {
		return System.currentTimeMillis() < flashBUntilMs;
	}

	private boolean isFlashingAnimation () {
		return System.currentTimeMillis() < flashAnimationUntilMs;
	}

	private boolean isFlashingBone () {
		return System.currentTimeMillis() < flashBoneUntilMs;
	}

	private void applyDropTargetStyles (boolean highlightA, boolean highlightB) {
		if (fileACard == null || fileBCard == null) return;
		if (fileACard.getBackground() != null && fileBCard.getBackground() != null
			&& lastHighlightA == highlightA && lastHighlightB == highlightB) return;
		lastHighlightA = highlightA;
		lastHighlightB = highlightB;
		fileACard.setBackground(skin.newDrawable("white", highlightA ? (isFlashingA() ? DROP_FLASH_A : DROP_HOVER_A) : DROP_BASE_A));
		fileBCard.setBackground(skin.newDrawable("white", highlightB ? (isFlashingB() ? DROP_FLASH_B : DROP_HOVER_B) : DROP_BASE_B));
	}

	private void applyDifferenceSectionStyles (boolean highlightAnimation, boolean highlightBone) {
		if (animationSectionCard != null) {
			if (animationSectionCard.getBackground() == null || lastHighlightAnimation != highlightAnimation) {
				lastHighlightAnimation = highlightAnimation;
				animationSectionCard.setBackground(skin.newDrawable("white", highlightAnimation ? SECTION_FLASH : SECTION_BASE));
			}
		}
		if (boneSectionCard != null) {
			if (boneSectionCard.getBackground() == null || lastHighlightBone != highlightBone) {
				lastHighlightBone = highlightBone;
				boneSectionCard.setBackground(skin.newDrawable("white", highlightBone ? SECTION_FLASH : SECTION_BASE));
			}
		}
		if (summarySectionCard != null && summarySectionCard.getBackground() == null)
			summarySectionCard.setBackground(skin.newDrawable("white", SECTION_BASE));
	}

	private boolean isPointerOver (Actor actor) {
		if (actor == null || !actor.isVisible()) return false;
		Vector2 stagePoint = stage.screenToStageCoordinates(new Vector2(Gdx.input.getX(), Gdx.input.getY()));
		Vector2 origin = actor.localToStageCoordinates(new Vector2(0, 0));
		return stagePoint.x >= origin.x && stagePoint.x <= origin.x + actor.getWidth() && stagePoint.y >= origin.y
			&& stagePoint.y <= origin.y + actor.getHeight();
	}

	private void flashDifferenceSections (boolean flashAnimation, boolean flashBone) {
		long flashUntil = System.currentTimeMillis() + 1400;
		if (flashAnimation) flashAnimationUntilMs = flashUntil;
		if (flashBone) flashBoneUntilMs = flashUntil;
		applyDifferenceSectionStyles(isFlashingAnimation(), isFlashingBone());
	}

	private void applyWidgetStyles () {
		window.setStyle(skin.get(WindowStyle.class));
		LabelStyle defaultLabelStyle = skin.get(LabelStyle.class);
		LabelStyle titleLabelStyle = skin.get("title", LabelStyle.class);
		TextButtonStyle defaultButtonStyle = skin.get(TextButtonStyle.class);

		loadedFilesSectionTitle.setStyle(titleLabelStyle);
		summarySectionTitle.setStyle(titleLabelStyle);
		animationSectionTitle.setStyle(titleLabelStyle);
		boneSectionTitle.setStyle(titleLabelStyle);
		fileATitleLabel.setStyle(titleLabelStyle);
		fileAHintLabel.setStyle(defaultLabelStyle);
		fileBTitleLabel.setStyle(titleLabelStyle);
		fileBHintLabel.setStyle(defaultLabelStyle);
		fileALabel.setStyle(defaultLabelStyle);
		fileBLabel.setStyle(defaultLabelStyle);
		statusLabel.setStyle(defaultLabelStyle);
		summaryLabel.setStyle(defaultLabelStyle);
		animationsOnlyATitle.setStyle(defaultLabelStyle);
		animationsOnlyBTitle.setStyle(defaultLabelStyle);
		bonesOnlyATitle.setStyle(defaultLabelStyle);
		bonesOnlyBTitle.setStyle(defaultLabelStyle);
		bonesChangedTitle.setStyle(defaultLabelStyle);

		reloadAButton.setStyle(defaultButtonStyle);
		reloadBButton.setStyle(defaultButtonStyle);
		languageButton.setStyle(defaultButtonStyle);
	}

	private Table createReloadControl (boolean loadA) {
		Table container = new Table(skin);
		container.defaults().left();
		container.add(loadA ? reloadAButton : reloadBButton).width(96).height(26);
		return container;
	}

	private String loadingText (boolean loadA) {
		if (!comparator.isLoading(loadA)) return comparator.t("Reload", "刷新");
		return comparator.isReloading(loadA) ? comparator.t("Reloading", "刷新中") : comparator.t("Loading", "加载中");
	}

	private void installChineseCapableFont () {
		FileHandle fontFile = Gdx.files.internal("skin/NotoSansSC-Regular.otf");
		if (!fontFile.exists()) return;
		try {
			fontGenerator = new FreeTypeFontGenerator(fontFile);
			FreeTypeFontParameter parameter = new FreeTypeFontParameter();
			parameter.size = Math.round(14 * SkeletonComparator.uiScale);
			parameter.incremental = true;
			parameter.hinting = FreeTypeFontGenerator.Hinting.Slight;
			parameter.minFilter = TextureFilter.Linear;
			parameter.magFilter = TextureFilter.Linear;
			generatedFont = fontGenerator.generateFont(parameter);
			generatedFont.getData().markupEnabled = true;

			skin.get(LabelStyle.class).font = generatedFont;
			skin.get("title", LabelStyle.class).font = generatedFont;
			skin.get(TextButtonStyle.class).font = generatedFont;
			skin.get("toggle", TextButtonStyle.class).font = generatedFont;
			skin.get(WindowStyle.class).titleFont = generatedFont;
		} catch (Throwable ex) {
			System.out.println("Failed to install Chinese font.");
			ex.printStackTrace();
		}
	}
}
