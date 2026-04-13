import * as spine from "@esotericsoftware/spine-core";
import { SpineCanvas, ResizeMode, Vector3 } from "@esotericsoftware/spine-webgl";

/** Unique ID suffix for each viewer instance (1 or 2) */
function el(id: string, instanceId: number) {
  return document.getElementById(`${id}-${instanceId}`)!;
}

const apps = new Map<number, App>();
let compareMode = false;

class App {
  private id: number;
  private spineCanvas: SpineCanvas | null = null;
  private axisColor = new spine.Color(0, 0, 0, 0.55);
  private showAxes: boolean = true;
  private usePremultipliedAlpha: boolean = false;

  skeleton: spine.Skeleton | null = null;
  animationState: spine.AnimationState | null = null;
  skeletonData: spine.SkeletonData | null = null;
  activeAnimName: string = "";
  selectedSlotIndex: number = -1;
  showBones: boolean = false;
  showNonEmptySlotsOnly: boolean = false;
  nonEmptySlotIndices: Set<number> = new Set();
  private isRendering: boolean = false;
  private shapesEnabledThisFrame: boolean = false;
  private baseZoom: number = 1;
  private detailsCollapsed: boolean = true;
  private backgroundIndex: number = 2;
  private currentFileName: string = "";
  private statusMessage: string = "";
  private backgroundPalette = [
    { label: "黑", canvas: [0.06, 0.06, 0.09, 1] as [number, number, number, number], surface: "#101018" },
    { label: "白", canvas: [0.96, 0.96, 0.96, 1] as [number, number, number, number], surface: "#f1f1f1" },
    { label: "深灰", canvas: [0.17, 0.17, 0.19, 1] as [number, number, number, number], surface: "#2a2a31" },
    { label: "浅灰", canvas: [0.8, 0.8, 0.82, 1] as [number, number, number, number], surface: "#cbcbcf" },
  ];

  // Pan state — per instance
  private isPanning: boolean = false;
  private panStartX: number = 0;
  private panStartY: number = 0;

  constructor(id: number) {
    this.id = id;
    this.wirePanZoom();
    this.wireFileInput();
    this.wireToggles();
    this.wirePanelCollapse();
    this.wireToolbarButtons();
  }

  // ── Asset loading ────────────────────────────────────────────────

  loadAssets(_canvas: SpineCanvas) {}

  initialize(canvas: SpineCanvas) {
    this.spineCanvas = canvas;
    this.applyBackground();
    this.updateViewerCaption();
  }

  playDefaultAnimation() {
    if (!this.skeletonData || !this.animationState) return;
    const idle = this.skeletonData.animations.find(a =>
      a.name.toLowerCase() === "idle" ||
      a.name.toLowerCase() === "stand" ||
      a.name.toLowerCase() === "walk"
    );
    if (idle) {
      this.activeAnimName = idle.name;
      this.animationState.setAnimation(0, idle.name, true);
    } else {
      console.log("[playDefaultAnimation] No idle found — staying in setup pose");
    }
  }

  loadFile(fileList: FileList) {
    if (!this.spineCanvas) return;
    const am = this.spineCanvas.assetManager as any;
    this.skeleton = null;
    this.skeletonData = null;
    this.animationState = null;
    this.selectedSlotIndex = -1;
    this.activeAnimName = "";
    this.statusMessage = "";

    const files = Array.from(fileList);
    const isBinarySkeletonFile = (name: string) => name.endsWith(".skel") || name.endsWith(".skel.bytes");
    const skelFile = files.find(f => isBinarySkeletonFile(f.name));
    const jsonFile = files.find(f => f.name.endsWith(".json"));
    const atlasFile = files.find(f => f.name.endsWith(".atlas") || f.name.endsWith(".atlas.txt"));
    const texFiles = files.filter(f => /\.(png|jpg|jpeg|gif|webp)$/i.test(f.name));

    const skeletonFile = skelFile ?? jsonFile;
    if (!skeletonFile || !atlasFile || texFiles.length === 0) {
      this.handleLoadFailure("读取失败。");
      return;
    }
    const displayPath = this.toDisplayPath(skeletonFile);

    let atlasTextContent = "";
    const textureDataUris = new Map<string, string>();

    const bufferToBase64 = (buffer: ArrayBuffer) => {
      let binary = "";
      const bytes = new Uint8Array(buffer);
      for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
      return window.btoa(binary);
    };

    const getMimeType = (fileName: string) => {
      const ext = fileName.split(".").pop()?.toLowerCase();
      switch (ext) {
        case "jpg":
        case "jpeg":
          return "image/jpeg";
        case "gif":
          return "image/gif";
        case "webp":
          return "image/webp";
        default:
          return "image/png";
      }
    };

    const basename = (fileName: string) => fileName.split("/").pop() ?? fileName;
    const atlasPageNames = (atlasText: string) => {
      const lines = atlasText.split(/\r?\n/);
      const pages: string[] = [];
      for (let i = 0; i < lines.length - 1; i++) {
        const current = lines[i].trim();
        const next = lines[i + 1].trim();
        if (!current || current.includes(":")) continue;
        if (next.startsWith("size:")) pages.push(current);
      }
      return pages;
    };

    const readTasks = files.map(async file => {
      if (file === atlasFile) {
        atlasTextContent = await file.text();
        am.setRawDataURI(file.name, "data:text/plain;," + encodeURIComponent(atlasTextContent));
      } else if (file === skelFile) {
        const skelBuffer = await file.arrayBuffer();
        am.setRawDataURI(file.name, "data:application/octet-stream;base64," + bufferToBase64(skelBuffer));
        am.loadBinary(file.name);
      } else if (file === jsonFile) {
        const jsonText = await file.text();
        am.setRawDataURI(file.name, "data:text/plain;," + encodeURIComponent(jsonText));
        am.loadJson(file.name);
      } else if (texFiles.includes(file)) {
        const texBuffer = await file.arrayBuffer();
        const dataUri = "data:" + getMimeType(file.name) + ";base64," + bufferToBase64(texBuffer);
        am.setRawDataURI(file.name, dataUri);
        textureDataUris.set(file.name, dataUri);
      }
    });

    Promise.all(readTasks)
      .then(() => {
        for (const pageName of atlasPageNames(atlasTextContent)) {
          const directMatch = textureDataUris.get(pageName);
          if (directMatch) {
            am.setRawDataURI(pageName, directMatch);
            continue;
          }
          const byBasename = [...textureDataUris.entries()].find(([name]) => basename(name) === basename(pageName));
          if (byBasename) am.setRawDataURI(pageName, byBasename[1]);
        }

        am.loadTextureAtlas(atlasFile.name);
        const waitForLoad = () => {
          if (am.isLoadingComplete()) {
            this.loadSkeleton(skeletonFile.name, atlasFile.name, undefined, displayPath);
          } else {
            requestAnimationFrame(waitForLoad);
          }
        };
        waitForLoad();
      })
      .catch(error => {
        console.error("[loadFile] Error:", error);
        this.handleLoadFailure("读取失败。");
      });
  }

  private loadSkeleton(skeletonPath: string, atlasPath: string, animationName?: string, displayPath?: string) {
    if (!this.spineCanvas) return;
    try {
      const assetManager = this.spineCanvas.assetManager as any;
      const atlas = assetManager.require(atlasPath) as spine.TextureAtlas;
      const atlasLoader = new spine.AtlasAttachmentLoader(atlas);
      const loader = (skeletonPath.endsWith(".skel") || skeletonPath.endsWith(".skel.bytes"))
        ? new spine.SkeletonBinary(atlasLoader)
        : new spine.SkeletonJson(atlasLoader);
      loader.scale = 1;
      this.skeletonData = loader.readSkeletonData(assetManager.require(skeletonPath));
      this.skeleton = new spine.Skeleton(this.skeletonData);
      const stateData = new spine.AnimationStateData(this.skeletonData);
      this.animationState = new spine.AnimationState(stateData);
      this.populateAll();
      if (animationName) {
        this.activeAnimName = animationName;
        this.animationState.setAnimation(0, animationName, true);
      } else {
        this.playDefaultAnimation();
      }
      this.currentFileName = displayPath ?? skeletonPath.replace("/spine/", "");
      this.statusMessage = "";
      this.highlightSelectedAnim();
      this.resetCamera();
      this.updateViewerCaption();
      refreshComparisonViews();
    } catch (e) {
      console.error("[loadSkeleton] Error:", e);
      this.handleLoadFailure("读取失败。");
    }
  }

  resetCamera() {
    if (!this.spineCanvas) return;
    const cam = (this.spineCanvas.renderer as any).camera;
    const canvasEl = el("canvas", this.id) as HTMLCanvasElement;
    if (!cam) return;
    if (!this.skeleton) return;

    this.animationState?.update(0);
    this.animationState?.apply(this.skeleton);
    this.skeleton.updateWorldTransform(spine.Physics.update);

    const offset = new spine.Vector2();
    const size = new spine.Vector2();
    this.skeleton.getBounds(offset, size, []);

    const width = Math.max(size.x, 1);
    const height = Math.max(size.y, 1);
    const canvasWidth = Math.max(canvasEl.clientWidth, 1);
    const canvasHeight = Math.max(canvasEl.clientHeight, 1);
    const canvasAspect = canvasWidth / canvasHeight;
    const skeletonAspect = width / height;
    const padding = 1.15;

    this.spineCanvas.renderer.resize(ResizeMode.Fit);
    cam.position.x = offset.x + width / 2;
    cam.position.y = offset.y + height / 2;

    if (skeletonAspect >= canvasAspect) {
      cam.viewportWidth = width * padding;
      cam.viewportHeight = cam.viewportWidth / canvasAspect;
    } else {
      cam.viewportHeight = height * padding;
      cam.viewportWidth = cam.viewportHeight * canvasAspect;
    }

    cam.zoom = 1;
    this.baseZoom = cam.zoom;
    cam.update();
  }

  // ── Pan & Zoom ──────────────────────────────────────────────────

  private wirePanZoom() {
    const canvasEl = el("canvas", this.id) as HTMLCanvasElement;
    const canvasWrap = el("canvas-wrap", this.id) as HTMLElement;

    canvasWrap.addEventListener("mousedown", (e: MouseEvent) => {
      if (e.button === 1) {
        e.preventDefault();
        this.isPanning = true;
        this.panStartX = e.clientX;
        this.panStartY = e.clientY;
        canvasWrap.style.cursor = "grabbing";
      }
    });

    window.addEventListener("mousemove", (e: MouseEvent) => {
      if (!this.isPanning || !this.spineCanvas) return;
      const cam = (this.spineCanvas.renderer as any).camera;
      if (!cam) return;

      const rect = canvasEl.getBoundingClientRect();
      const previousWorld = cam.screenToWorld(
        new Vector3(this.panStartX - rect.left, this.panStartY - rect.top, 0),
        canvasEl.clientWidth,
        canvasEl.clientHeight
      );
      const currentWorld = cam.screenToWorld(
        new Vector3(e.clientX - rect.left, e.clientY - rect.top, 0),
        canvasEl.clientWidth,
        canvasEl.clientHeight
      );
      cam.position.add(previousWorld.sub(currentWorld));
      cam.update();
      this.panStartX = e.clientX;
      this.panStartY = e.clientY;
    });

    window.addEventListener("mouseup", (e: MouseEvent) => {
      if (e.button === 1) {
        this.isPanning = false;
        canvasWrap.style.cursor = "grab";
      }
    });

    canvasWrap.addEventListener("wheel", (e: WheelEvent) => {
      e.preventDefault();
      if (!this.spineCanvas) return;
      const cam = (this.spineCanvas.renderer as any).camera;
      if (!cam) return;

      const rect = canvasEl.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const mouseY = e.clientY - rect.top;
      const anchorBeforeZoom = cam.screenToWorld(
        new Vector3(mouseX, mouseY, 0),
        canvasEl.clientWidth,
        canvasEl.clientHeight
      );

      const zoomFactor = Math.exp(e.deltaY * 0.0015);
      const nextZoom = Math.max(cam.zoom * zoomFactor, 0.0001);
      cam.zoom = nextZoom;
      cam.update();

      const anchorAfterZoom = cam.screenToWorld(
        new Vector3(mouseX, mouseY, 0),
        canvasEl.clientWidth,
        canvasEl.clientHeight
      );
      cam.position.add(anchorBeforeZoom.sub(anchorAfterZoom));
      cam.update();
    }, { passive: false });

    canvasWrap.addEventListener("dblclick", () => {
      this.resetCamera();
    });
  }

  // ── File input ─────────────────────────────────────────────────

  private wireFileInput() {
    const fileInput = el("file-input", this.id) as HTMLInputElement;
    fileInput.addEventListener("change", () => {
      if (fileInput.files && fileInput.files.length > 0) {
        this.loadFile(fileInput.files);
      }
    });
  }

  private wireToolbarButtons() {
    const loadButton = el("load-btn", this.id) as HTMLButtonElement;
    const clearButton = el("clear-btn", this.id) as HTMLButtonElement;
    const backgroundButton = el("bg-btn", this.id) as HTMLButtonElement;
    const crosshairButton = el("crosshair-btn", this.id) as HTMLButtonElement;
    const alphaButton = el("alpha-btn", this.id) as HTMLButtonElement;
    const fileInput = el("file-input", this.id) as HTMLInputElement;

    loadButton.addEventListener("click", () => {
      fileInput.value = "";
      fileInput.click();
    });

    clearButton.addEventListener("click", () => {
      this.clearViewer();
    });

    backgroundButton.addEventListener("click", () => {
      this.backgroundIndex = (this.backgroundIndex + 1) % this.backgroundPalette.length;
      this.applyBackground();
    });

    crosshairButton.addEventListener("click", () => {
      this.showAxes = !this.showAxes;
      this.updateToolbarTitles();
    });

    alphaButton.addEventListener("click", () => {
      this.usePremultipliedAlpha = !this.usePremultipliedAlpha;
      this.updateToolbarTitles();
    });

    this.updateToolbarTitles();
  }

  // ── Toggles ─────────────────────────────────────────────────────

  private wireToggles() {
    const toggleBonesBtn = el("toggle-bones", this.id) as HTMLButtonElement;
    toggleBonesBtn.addEventListener("click", () => {
      this.showBones = !this.showBones;
      toggleBonesBtn.classList.toggle("on", this.showBones);
      console.log(`[viewer${this.id}] toggle bones:`, this.showBones);
    });

    const toggleSlotsFilterBtn = el("toggle-slots-filter", this.id) as HTMLButtonElement;
    toggleSlotsFilterBtn.addEventListener("click", () => {
      this.showNonEmptySlotsOnly = !this.showNonEmptySlotsOnly;
      toggleSlotsFilterBtn.classList.toggle("on", this.showNonEmptySlotsOnly);
      this.populateSlots();
      console.log(`[viewer${this.id}] toggle slots filter:`, this.showNonEmptySlotsOnly);
    });
  }

  private wirePanelCollapse() {
    const detailsBar = el("details-bar", this.id) as HTMLElement;
    const button = el("toggle-details", this.id) as HTMLButtonElement;

    const renderState = () => {
      detailsBar.classList.toggle("collapsed", this.detailsCollapsed);
      button.textContent = this.detailsCollapsed ? "展开" : "收起";
      button.setAttribute("aria-expanded", String(!this.detailsCollapsed));
    };

    button.addEventListener("click", () => {
      this.detailsCollapsed = !this.detailsCollapsed;
      renderState();
    });

    renderState();
  }

  private updateToolbarTitles() {
    const loadButton = el("load-btn", this.id) as HTMLButtonElement;
    const clearButton = el("clear-btn", this.id) as HTMLButtonElement;
    const backgroundButton = el("bg-btn", this.id) as HTMLButtonElement;
    const crosshairButton = el("crosshair-btn", this.id) as HTMLButtonElement;
    const alphaButton = el("alpha-btn", this.id) as HTMLButtonElement;
    loadButton.title = "加载 .skel / .atlas / .png";
    clearButton.title = "清除当前 viewer";
    backgroundButton.title = `背景色: ${this.backgroundPalette[this.backgroundIndex].label}`;
    crosshairButton.title = `十字线: ${this.showAxes ? "开" : "关"}`;
    alphaButton.title = `预乘 alpha: ${this.usePremultipliedAlpha ? "开" : "关"}`;
    crosshairButton.classList.toggle("on", this.showAxes);
    alphaButton.classList.toggle("on", this.usePremultipliedAlpha);
  }

  private updateViewerCaption() {
    const caption = el("viewer-caption", this.id) as HTMLElement;
    const fileText = this.currentFileName ? `<strong>文件</strong> ${this.currentFileName}` : `<strong>文件</strong> 未加载`;
    const animationValue = this.activeAnimName
      ? `<span class="playing-inline"><span class="playing-dot"></span>${this.activeAnimName}</span>`
      : "无";
    const animationText = `<strong>动画</strong> ${animationValue}`;
    const statusText = this.statusMessage ? `<span class="caption-status">${this.statusMessage}</span>` : "";
    caption.innerHTML = `${fileText}<span>${animationText}</span>${statusText}`;
  }

  private applyBackground() {
    const canvasWrap = el("canvas-wrap", this.id) as HTMLElement;
    const palette = this.backgroundPalette[this.backgroundIndex];
    canvasWrap.style.background = palette.surface;
    this.updateToolbarTitles();
  }

  private clearViewer() {
    this.skeleton = null;
    this.animationState = null;
    this.skeletonData = null;
    this.activeAnimName = "";
    this.currentFileName = "";
    this.selectedSlotIndex = -1;
    this.nonEmptySlotIndices.clear();
    this.statusMessage = "";

    (el("anim-list", this.id) as HTMLElement).innerHTML = "";
    (el("bone-list", this.id) as HTMLElement).innerHTML = "";
    (el("slot-list", this.id) as HTMLElement).innerHTML = "";
    (el("metrics-list", this.id) as HTMLElement).innerHTML = "";
    (el("anim-count", this.id) as HTMLElement).textContent = "0";
    (el("bone-count", this.id) as HTMLElement).textContent = "0";
    (el("slot-count", this.id) as HTMLElement).textContent = "0";
    this.updateViewerCaption();
    refreshComparisonViews();
  }

  private toDisplayPath(file: File) {
    const rawPath = ((file as any).webkitRelativePath as string | undefined)
      || ((file as any).path as string | undefined)
      || file.name;
    const normalized = rawPath.replace(/\\/g, "/");
    const segments = normalized.split("/").filter(Boolean);
    if (segments.length >= 2) return `/${segments[segments.length - 2]}/${segments[segments.length - 1]}`;
    return file.name;
  }

  private handleLoadFailure(message: string) {
    this.clearViewer();
    this.statusMessage = message;
    this.updateViewerCaption();
    window.alert(message);
  }

  // ── Populate UI ────────────────────────────────────────────────

  private populateAll() {
    this.populateAnims();
    this.populateBones();
    this.populateSlots();
    this.populateMetrics();
  }

  refreshVisibleData() {
    if (!this.skeletonData) {
      (el("anim-count", this.id) as HTMLElement).textContent = "0";
      (el("bone-count", this.id) as HTMLElement).textContent = "0";
      (el("slot-count", this.id) as HTMLElement).textContent = "0";
      this.setCompareHighlight("anim-panel", false);
      this.setDetailSectionHighlight("bone-list", false);
      this.setDetailSectionHighlight("slot-list", false);
      return;
    }
    this.populateAnims();
    this.populateBones();
    this.populateSlots();
  }

  private populateAnims() {
    const list = el("anim-list", this.id);
    const countEl = el("anim-count", this.id);
    const allAnimations = this.skeletonData!.animations;
    const displayedAnimations = compareMode ? allAnimations.filter(anim => !this.getPeerAnimationNames().has(anim.name)) : allAnimations;
    countEl.textContent = compareMode ? `${displayedAnimations.length}/${allAnimations.length}` : String(allAnimations.length);
    list.innerHTML = "";
    this.setCompareHighlight("anim-panel", compareMode && displayedAnimations.length > 0);
    displayedAnimations.forEach(anim => {
      const li = document.createElement("li");
      li.className = "item";
      li.dataset.animName = anim.name;
      const isActive = anim.name === this.activeAnimName;
      li.innerHTML = isActive
        ? `<span class="list-item-content"><span class="playing-dot"></span><span>${anim.name}</span></span>`
        : `<span class="list-item-content"><span class="list-item-bullet">•</span><span>${anim.name}</span></span>`;
      li.addEventListener("click", () => {
        this.activeAnimName = anim.name;
        this.animationState!.setAnimation(0, anim.name, true);
        this.populateAnims();
        this.updateViewerCaption();
      });
      list.appendChild(li);
    });
    this.highlightSelectedAnim();
  }

  private highlightSelectedAnim() {
    el("anim-list", this.id).querySelectorAll(".item").forEach(el => {
      const isActive = (el as HTMLElement).dataset.animName === this.activeAnimName;
      el.classList.toggle("active", isActive);
    });
  }

  private populateBones() {
    const list = el("bone-list", this.id);
    const countEl = el("bone-count", this.id);
    const allBones = this.skeletonData!.bones;
    const displayedBones = compareMode ? allBones.filter(bone => !this.getPeerBoneNames().has(bone.name)) : allBones;
    countEl.textContent = compareMode ? `${displayedBones.length}/${allBones.length}` : String(allBones.length);
    list.innerHTML = "";
    this.setDetailSectionHighlight("bone-list", compareMode && displayedBones.length > 0);
    displayedBones.forEach(bone => {
      const li = document.createElement("li");
      li.className = "item";
      li.innerHTML = `<span class="list-item-content"><span class="list-item-bullet">•</span><span>${bone.name}</span></span>`;
      list.appendChild(li);
    });
  }

  private populateSlots() {
    const list = el("slot-list", this.id);
    const countEl = el("slot-count", this.id);
    const allSlots = this.skeletonData!.slots;
    const total = allSlots.length;

    // Build set of slot indices that qualify as "non-empty":
    // 1) bone is active  2) has a visible attachment  3) alpha = 100%
    this.nonEmptySlotIndices.clear();
    this.skeleton!.slots.forEach((slot, idx) => {
      if (!slot.bone.active) return;
      if (!slot.getAttachment()) return;
      if (slot.color.a < 1) return;
      this.nonEmptySlotIndices.add(idx);
    });

    let displayed = compareMode
      ? allSlots.filter(slot => !this.getPeerSlotNames().has(slot.name))
      : allSlots;

    displayed = this.showNonEmptySlotsOnly
      ? displayed.filter(slot => {
          const originalIndex = this.skeletonData!.slots.findIndex(candidate => candidate.name === slot.name);
          return originalIndex >= 0 && this.nonEmptySlotIndices.has(originalIndex);
        })
      : displayed;

    countEl.textContent = `${displayed.length}/${total}`;
    list.innerHTML = "";
    this.setDetailSectionHighlight("slot-list", compareMode && displayed.length > 0);
    displayed.forEach(slot => {
      const originalIdx = this.skeletonData!.slots.findIndex(candidate => candidate.name === slot.name);
      const li = document.createElement("li");
      li.className = "item";
      li.dataset.slotIndex = String(originalIdx);
      li.innerHTML = `<span class="list-item-content"><span class="list-item-bullet">•</span><span>${slot.name}</span></span>`;
      li.addEventListener("click", () => {
        this.selectedSlotIndex = originalIdx;
        this.highlightSelectedSlot();
      });
      list.appendChild(li);
    });
  }

  private getPeerApp() {
    return apps.get(this.id === 1 ? 2 : 1) ?? null;
  }

  private getPeerAnimationNames() {
    const peer = this.getPeerApp();
    return peer?.getAnimationNameSet() ?? new Set<string>();
  }

  private getPeerBoneNames() {
    const peer = this.getPeerApp();
    return peer?.getBoneNameSet() ?? new Set<string>();
  }

  private getPeerSlotNames() {
    const peer = this.getPeerApp();
    return peer?.getSlotNameSet() ?? new Set<string>();
  }

  getAnimationNameSet() {
    return new Set(this.skeletonData?.animations.map(animation => animation.name) ?? []);
  }

  getBoneNameSet() {
    return new Set(this.skeletonData?.bones.map(bone => bone.name) ?? []);
  }

  getSlotNameSet() {
    return new Set(this.skeletonData?.slots.map(slot => slot.name) ?? []);
  }

  private setCompareHighlight(sectionId: string, active: boolean) {
    const section = el(sectionId, this.id) as HTMLElement;
    section.classList.toggle("compare-diff", active);
  }

  private setDetailSectionHighlight(listId: string, active: boolean) {
    const list = el(listId, this.id) as HTMLElement;
    const section = list.closest(".detail-section") as HTMLElement | null;
    section?.classList.toggle("compare-diff", active);
  }

  private populateMetrics() {
    if (!this.skeletonData) return;
    const list = el("metrics-list", this.id);
    const constraints =
      this.skeletonData.ikConstraints.length +
      this.skeletonData.transformConstraints.length +
      this.skeletonData.pathConstraints.length;

    const metrics: Array<[string, number | string]> = [
      ["Bones", this.skeletonData.bones.length],
      ["Bone Transforms", this.countBoneTransformTimelines()],
      ["Constraints", constraints],
      ["Slots", this.skeletonData.slots.length],
      ["Animations", this.skeletonData.animations.length],
    ];

    list.innerHTML = "";
    metrics.forEach(([label, value]) => {
      const item = document.createElement("li");
      const labelEl = document.createElement("span");
      const valueEl = document.createElement("span");
      labelEl.className = "metric-label";
      valueEl.className = "metric-value";
      labelEl.textContent = label;
      valueEl.textContent = String(value);
      item.appendChild(labelEl);
      item.appendChild(valueEl);
      list.appendChild(item);
    });
  }

  private countBoneTransformTimelines() {
    if (!this.skeletonData) return 0;
    const boneTimelineTypes = [
      spine.RotateTimeline,
      spine.TranslateTimeline,
      spine.TranslateXTimeline,
      spine.TranslateYTimeline,
      spine.ScaleTimeline,
      spine.ScaleXTimeline,
      spine.ScaleYTimeline,
      spine.ShearTimeline,
      spine.ShearXTimeline,
      spine.ShearYTimeline,
      spine.InheritTimeline,
    ];

    let count = 0;
    this.skeletonData.animations.forEach(animation => {
      animation.timelines.forEach(timeline => {
        if (boneTimelineTypes.some(type => timeline instanceof type)) count++;
      });
    });
    return count;
  }

  private highlightSelectedSlot() {
    el("slot-list", this.id).querySelectorAll(".item").forEach(el => {
      const isSelected = Number((el as HTMLElement).dataset.slotIndex) === this.selectedSlotIndex;
      el.classList.toggle("active", isSelected);
    });
  }

  // ── Render loop callbacks ──────────────────────────────────────

  update(_canvas: SpineCanvas, delta: number) {
    if (!this.skeleton || !this.animationState) return;
    this.animationState.update(delta);
    this.animationState.apply(this.skeleton);
    this.skeleton.updateWorldTransform(spine.Physics.none);
  }

  render(canvas: SpineCanvas) {
    const background = this.backgroundPalette[this.backgroundIndex].canvas;
    if (this.isRendering) return;
    if (!this.skeleton || !this.animationState) {
      canvas.clear(background[0], background[1], background[2], background[3]);
      this.updateZoomDisplay();
      return;
    }
    this.isRendering = true;
    try {
      this.shapesEnabledThisFrame = false;

      const renderer = canvas.renderer;
      renderer.resize(ResizeMode.Fit);

      const cam = (renderer as any).camera;
      if (cam) {
        if (cam.zoom < 0.05) cam.zoom = 0.05;
        cam.update();
      }

      canvas.clear(background[0], background[1], background[2], background[3]);
      renderer.begin();

      if (this.showAxes) this.drawAxes(renderer);

      renderer.drawSkeleton(this.skeleton, this.usePremultipliedAlpha);

      if (this.selectedSlotIndex >= 0 && this.skeleton) {
        if (!this.shapesEnabledThisFrame) {
          (renderer as any).enableRenderer((renderer as any).shapes);
          this.shapesEnabledThisFrame = true;
        }
        const t = (canvas as any).time?.seconds ?? 0;
        const pulse = (Math.sin(t * 3.0) + 1) * 0.5;
        this.drawSlotOutline(renderer, pulse);
      }

      if (this.showBones && this.skeleton) {
        if (!this.shapesEnabledThisFrame) {
          (renderer as any).enableRenderer((renderer as any).shapes);
          this.shapesEnabledThisFrame = true;
        }
        this.drawBones((renderer as any).shapes);
      }

      renderer.end();
      this.updateZoomDisplay();
    } finally {
      this.isRendering = false;
    }
  }

  updateZoomDisplay() {
    if (!this.spineCanvas) return;
    const cam = (this.spineCanvas.renderer as any).camera;
    const indicator = el("zoom-indicator", this.id);
    if (cam && indicator) {
      const percent = this.baseZoom > 0 ? (this.baseZoom / cam.zoom) * 100 : 100;
      indicator.textContent = Math.round(percent) + "%";
    }
  }

  // ── Debug draw helpers ──────────────────────────────────────────

  drawBones(shapes: any) {
    if (!this.skeleton) return;
    const cyan = new spine.Color(0, 1, 1, 1);
    shapes.setColor(cyan);
    const bones = this.skeleton.bones;
    for (let i = 0; i < bones.length; i++) {
      const bone = bones[i];
      if (!bone.parent) continue;
      const len = bone.data.length;
      if (len < 1) continue;
      const ex = len * bone.a + bone.worldX;
      const ey = len * bone.c + bone.worldY;
      shapes.rectLine(true, bone.worldX, bone.worldY, ex, ey, 2);
    }
    // Yellow joint dots
    const dotColor = new spine.Color(1, 1, 0, 1);
    shapes.setColor(dotColor);
    for (let i = 0; i < bones.length; i++) {
      shapes.circle(true, bones[i].worldX, bones[i].worldY, 3, dotColor, 8);
    }
  }

  private drawAxes(renderer: any) {
    const cam = renderer.camera;
    if (!cam) return;

    const halfWidth = (cam.viewportWidth * cam.zoom) / 2;
    const halfHeight = (cam.viewportHeight * cam.zoom) / 2;
    const left = cam.position.x - halfWidth;
    const right = cam.position.x + halfWidth;
    const bottom = cam.position.y - halfHeight;
    const top = cam.position.y + halfHeight;

    renderer.line(left, 0, right, 0, this.axisColor);
    renderer.line(0, bottom, 0, top, this.axisColor);
  }

  drawSlotOutline(renderer: any, pulse: number = 0) {
    if (!this.skeleton) return;
    const slot = this.skeleton.slots[this.selectedSlotIndex];
    if (!slot || !slot.bone.active) return;

    const attachment = slot.getAttachment();
    if (!attachment) return;

    const shapes = (renderer as any).shapes;
    if (!shapes) return;

    const vertices = new Float32Array(64);
    let count = 0;

    if (attachment instanceof spine.RegionAttachment) {
      attachment.computeWorldVertices(slot, vertices, 0, 2);
      count = 8;
    } else if (attachment instanceof spine.MeshAttachment) {
      count = Math.min(attachment.worldVerticesLength, 64);
      attachment.computeWorldVertices(slot, 0, count, vertices, 0, 2);
    } else {
      return;
    }

    if (count < 6) return;

    // Draw polygon outline
    const outlineAlpha = 0.6 + pulse * 0.4;
    const highlightColor = new spine.Color(0.3 + pulse * 0.3, 0.9, 1.0, outlineAlpha);
    shapes.setColor(highlightColor);
    shapes.polygon(vertices, 0, count);

    // Filled rectangle with breathing alpha
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (let i = 0; i < count; i += 2) {
      const vx = vertices[i];
      const vy = vertices[i + 1];
      if (vx < minX) minX = vx;
      if (vx > maxX) maxX = vx;
      if (vy < minY) minY = vy;
      if (vy > maxY) maxY = vy;
    }
    const fillAlpha = 0.1 + pulse * 0.25;
    const fillColor = new spine.Color(0.3 + pulse * 0.3, 0.9, 1.0, fillAlpha);
    shapes.setColor(fillColor);
    shapes.rect(true, minX, minY, maxX - minX, maxY - minY);
  }
}

// ── Bootstrap two viewer instances ─────────────────────────────────

function boot(id: number) {
  const canvasEl = el("canvas", id) as HTMLCanvasElement;
  const app = new App(id);
  apps.set(id, app);

  const spineCanvas = new SpineCanvas(canvasEl, {
    app: {
      loadAssets: (canvas: SpineCanvas) => app.loadAssets(canvas),
      initialize: (canvas: SpineCanvas) => app.initialize(canvas),
      update: (canvas: SpineCanvas, delta: number) => app.update(canvas, delta),
      render: (canvas: SpineCanvas) => app.render(canvas),
    },
  });
  void spineCanvas;

  canvasEl.style.width = "100%";
  canvasEl.style.height = "100%";
}

function refreshComparisonViews() {
  apps.forEach(app => app.refreshVisibleData());
}

function updateCompareToggleButton() {
  const button = document.getElementById("compare-toggle") as HTMLButtonElement | null;
  if (!button) return;
  button.classList.toggle("on", compareMode);
  button.textContent = compareMode ? "对比中" : "对比";
}

function wireCompareToggle() {
  const button = document.getElementById("compare-toggle") as HTMLButtonElement | null;
  if (!button) return;
  button.addEventListener("click", () => {
    compareMode = !compareMode;
    updateCompareToggleButton();
    refreshComparisonViews();
  });
  updateCompareToggleButton();
}

wireCompareToggle();
boot(1);
boot(2);
