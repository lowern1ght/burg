import { mat4, vec3 } from 'gl-matrix';

type Vec3 = [number, number, number];

export class OrbitCamera {
  private canvas: HTMLCanvasElement;
  center: Vec3;
  distance: number;
  private xRotation = 0.62;
  private yRotation = 0.82;
  private dragging = false;
  private panning = false;
  private lastPos: [number, number] | null = null;
  private onChange: () => void;
  private home: Vec3;

  private cleanupFns: (() => void)[] = [];
  onEditClick: ((button: number, ndcX: number, ndcY: number) => void) | null = null;
  onHover: ((ndcX: number, ndcY: number) => void) | null = null;
  editMode = false;
  private downButton = -1;
  private downPos: [number, number] | null = null;

  constructor(
    canvas: HTMLCanvasElement,
    center: Vec3,
    distance: number,
    onChange: () => void,
  ) {
    this.canvas = canvas;
    this.center = [...center] as Vec3;
    this.home = [...center] as Vec3;
    this.distance = distance;
    this.onChange = onChange;
    this.attach();
  }

  private isCameraButton(button: number): boolean {
    if (this.editMode) {
      // In edit mode: only middle-mouse or shift+left orbits the camera.
      // Left/right clicks are reserved for editing.
      return button === 1;
    }
    // In view mode: left orbits, middle/right/shift pans.
    return button === 0 || button === 1 || button === 2;
  }

  private attach() {
    const onPointerDown = (event: PointerEvent) => {
      this.downButton = event.button;
      this.downPos = [event.clientX, event.clientY];

      if (this.isCameraButton(event.button)) {
        this.dragging = true;
        this.panning = event.button === 1 || event.button === 2 || event.shiftKey;
        this.lastPos = [event.clientX, event.clientY];
        this.canvas.setPointerCapture?.(event.pointerId);
      } else {
        // Edit click: capture so pointerup fires on this element
        this.canvas.setPointerCapture?.(event.pointerId);
      }
    };

    const onContext = (event: Event) => event.preventDefault();

    const stopDragging = (event?: PointerEvent) => {
      // Check for edit click: if the button was an edit button and pointer barely moved
      if (this.downPos && event && this.onEditClick && !this.isCameraButton(this.downButton)) {
        const dx = event.clientX - this.downPos[0];
        const dy = event.clientY - this.downPos[1];
        if (Math.abs(dx) < 5 && Math.abs(dy) < 5) {
          const rect = this.canvas.getBoundingClientRect();
          const ndcX = ((event.clientX - rect.left) / rect.width) * 2 - 1;
          const ndcY = -((event.clientY - rect.top) / rect.height) * 2 + 1;
          this.onEditClick(this.downButton, ndcX, ndcY);
        }
      }

      this.downButton = -1;
      this.downPos = null;
      this.dragging = false;
      this.lastPos = null;
      if (event?.pointerId !== undefined && this.canvas.hasPointerCapture?.(event.pointerId)) {
        this.canvas.releasePointerCapture(event.pointerId);
      }
    };

    const onPointerMove = (event: PointerEvent) => {
      // Hover tracking for edit cursor
      if (!this.dragging && this.onHover) {
        const rect = this.canvas.getBoundingClientRect();
        const ndcX = ((event.clientX - rect.left) / rect.width) * 2 - 1;
        const ndcY = -((event.clientY - rect.top) / rect.height) * 2 + 1;
        this.onHover(ndcX, ndcY);
      }
      if (!this.dragging || !this.lastPos) return;
      const dx = event.clientX - this.lastPos[0];
      const dy = event.clientY - this.lastPos[1];
      if (this.panning) {
        this.pan(-dx, dy);
      } else {
        this.yRotation += dx / 160;
        this.xRotation += dy / 160;
        this.xRotation = Math.max(-1.05, Math.min(1.35, this.xRotation));
      }
      this.lastPos = [event.clientX, event.clientY];
      this.onChange();
    };

    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const distance = Math.max(4, Math.min(500, this.distance + event.deltaY * 0.08));
      if (distance !== this.distance) {
        this.distance = distance;
        this.onChange();
      }
    };

    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (target && (target.tagName === 'INPUT' || target.tagName === 'SELECT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
        return;
      }
      if (event.ctrlKey || event.metaKey || event.altKey) return;

      const step = Math.max(1, this.distance * 0.06);
      const key = event.key.toLowerCase();
      if (key === 'a') this.pan(-step * 6, 0);
      else if (key === 'd') this.pan(step * 6, 0);
      else if (key === 'w') this.pan(0, step * 6);
      else if (key === 's') this.pan(0, -step * 6);
      else if (key === 'q') this.center[1] -= step;
      else if (key === 'e') this.center[1] += step;
      else if (key === 'r') this.center = [...this.home] as Vec3;
      else return;
      event.preventDefault();
      this.onChange();
    };

    this.canvas.addEventListener('pointerdown', onPointerDown);
    this.canvas.addEventListener('contextmenu', onContext);
    this.canvas.addEventListener('pointerup', stopDragging);
    this.canvas.addEventListener('pointercancel', stopDragging);
    this.canvas.addEventListener('lostpointercapture', stopDragging);
    this.canvas.addEventListener('pointermove', onPointerMove);
    this.canvas.addEventListener('wheel', onWheel, { passive: false } as AddEventListenerOptions);
    window.addEventListener('keydown', onKeyDown);

    this.cleanupFns.push(
      () => this.canvas.removeEventListener('pointerdown', onPointerDown),
      () => this.canvas.removeEventListener('contextmenu', onContext),
      () => this.canvas.removeEventListener('pointerup', stopDragging),
      () => this.canvas.removeEventListener('pointercancel', stopDragging),
      () => this.canvas.removeEventListener('lostpointercapture', stopDragging),
      () => this.canvas.removeEventListener('pointermove', onPointerMove),
      () => this.canvas.removeEventListener('wheel', onWheel),
      () => window.removeEventListener('keydown', onKeyDown),
    );
  }

  pan(dxScreen: number, dyScreen: number) {
    const t = this.yRotation;
    const f = this.xRotation;
    const right: Vec3 = [Math.cos(t), 0, Math.sin(t)];
    const up: Vec3 = [Math.sin(f) * Math.sin(t), Math.cos(f), -Math.sin(f) * Math.cos(t)];
    const k = this.distance / 900;
    for (let i = 0; i < 3; i += 1) {
      this.center[i] += (right[i] * dxScreen + up[i] * dyScreen) * k;
    }
  }

  getView(): mat4 {
    const view = mat4.create();
    mat4.translate(view, view, [0, 0, -this.distance]);
    mat4.rotateX(view, view, this.xRotation);
    mat4.rotateY(view, view, this.yRotation);
    mat4.translate(view, view, [-this.center[0], -this.center[1], -this.center[2]]);
    return view;
  }

  getCamera() {
    const inverse = mat4.create();
    mat4.invert(inverse, this.getView());
    return {
      position: vec3.fromValues(inverse[12], inverse[13], inverse[14]),
      target: vec3.fromValues(this.center[0], this.center[1], this.center[2]),
    };
  }

  dispose() {
    this.cleanupFns.forEach(fn => fn());
    this.cleanupFns = [];
  }
}
