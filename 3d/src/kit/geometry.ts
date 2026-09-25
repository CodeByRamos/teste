// Procedural building blocks (millimetres). Every function returns a non-indexed BufferGeometry with
// position + normal only, centred on its own origin unless stated otherwise, so pieces can be merged freely.

import * as THREE from "three";
import { RoundedBoxGeometry } from "three/examples/jsm/geometries/RoundedBoxGeometry.js";
import { mergeGeometries } from "three/examples/jsm/utils/BufferGeometryUtils.js";

export type Axis = "x" | "y" | "z";
export type Detail = "high" | "low";

/** Strip to position/normal and drop indices so any two geometries can be merged. */
export function clean(geometry: THREE.BufferGeometry): THREE.BufferGeometry {
  const g = geometry.index ? geometry.toNonIndexed() : geometry.clone();
  for (const name of Object.keys(g.attributes)) {
    if (name !== "position" && name !== "normal") g.deleteAttribute(name);
  }
  if (!g.getAttribute("normal")) g.computeVertexNormals();
  return g;
}

export function merge(parts: THREE.BufferGeometry[]): THREE.BufferGeometry {
  const merged = mergeGeometries(parts.map(clean), false);
  if (!merged) throw new Error("Could not merge geometries");
  return merged;
}

export function at(geometry: THREE.BufferGeometry, x: number, y: number, z: number): THREE.BufferGeometry {
  return geometry.translate(x, y, z);
}

export function box(w: number, h: number, d: number): THREE.BufferGeometry {
  return clean(new THREE.BoxGeometry(w, h, d));
}

/** Box with rounded edges; radius is clamped so thin panels stay valid. */
export function roundedBox(w: number, h: number, d: number, radius: number, segments = 2): THREE.BufferGeometry {
  const r = Math.max(0.01, Math.min(radius, w / 2 - 0.01, h / 2 - 0.01, d / 2 - 0.01));
  return clean(new RoundedBoxGeometry(w, h, d, segments, r));
}

function orient(geometry: THREE.BufferGeometry, axis: Axis): THREE.BufferGeometry {
  // THREE cylinders are built along Y.
  if (axis === "x") geometry.rotateZ(Math.PI / 2);
  if (axis === "z") geometry.rotateX(Math.PI / 2);
  return geometry;
}

export function cylinder(radius: number, length: number, axis: Axis, segments = 32): THREE.BufferGeometry {
  return clean(orient(new THREE.CylinderGeometry(radius, radius, length, segments), axis));
}

/** Flat ring (washer) of given thickness along `axis`. */
export function ring(outer: number, inner: number, thickness: number, axis: Axis, segments = 48): THREE.BufferGeometry {
  const shape = new THREE.Shape().absarc(0, 0, outer, 0, Math.PI * 2, false);
  shape.holes.push(new THREE.Path().absarc(0, 0, inner, 0, Math.PI * 2, true));
  const g = new THREE.ExtrudeGeometry(shape, { depth: thickness, bevelEnabled: false, curveSegments: segments });
  g.translate(0, 0, -thickness / 2);
  // Extrusion runs along Z.
  if (axis === "x") g.rotateY(Math.PI / 2);
  if (axis === "y") g.rotateX(Math.PI / 2);
  return clean(g);
}

/**
 * Fan rotor: hub plus curved, pitched blades. Built along Z, re-oriented to `axis`.
 * Blades are the silhouette people recognise, so they get the most care; everything else is simple.
 */
export function fanRotor(diameter: number, thickness: number, axis: Axis, detail: Detail, blades = 7): THREE.BufferGeometry {
  const r = diameter / 2;
  const hubR = diameter * 0.17;
  const pieces: THREE.BufferGeometry[] = [];
  const arcSteps = detail === "high" ? 10 : 5;
  for (let b = 0; b < blades; b++) {
    const shape = new THREE.Shape();
    // Blade outline in polar coordinates: sweeps ~40° from hub to tip.
    const sweep = 0.7;
    const width = (Math.PI * 2) / blades * 0.72;
    for (let i = 0; i <= arcSteps; i++) {
      const t = i / arcSteps;
      const rad = hubR + (r - hubR - 1) * t;
      const ang = t * sweep;
      const x = Math.cos(ang) * rad;
      const y = Math.sin(ang) * rad;
      if (i === 0) shape.moveTo(x, y);
      else shape.lineTo(x, y);
    }
    for (let i = arcSteps; i >= 0; i--) {
      const t = i / arcSteps;
      const rad = hubR + (r - hubR - 1) * t;
      const ang = t * sweep + width * (0.55 + 0.45 * t);
      shape.lineTo(Math.cos(ang) * rad, Math.sin(ang) * rad);
    }
    const blade = new THREE.ExtrudeGeometry(shape, { depth: 1.2, bevelEnabled: false, curveSegments: 4 });
    blade.translate(0, 0, -0.6);
    // Pitch the blade around its radial direction to read as a propeller, not a flat disc.
    const mid = sweep / 2 + width / 2;
    const radial = new THREE.Vector3(Math.cos(mid), Math.sin(mid), 0);
    blade.applyMatrix4(new THREE.Matrix4().makeRotationAxis(radial, 0.42));
    blade.rotateZ((b / blades) * Math.PI * 2);
    pieces.push(blade);
  }
  const hub = new THREE.CylinderGeometry(hubR, hubR, thickness, detail === "high" ? 32 : 16);
  hub.rotateX(Math.PI / 2);
  pieces.push(hub);
  const rotor = merge(pieces);
  // Pitched blades can be deeper than the declared thickness; flatten them into it so the part never exceeds
  // the size the layout reserves for it.
  rotor.computeBoundingBox();
  const depth = rotor.boundingBox!.max.z - rotor.boundingBox!.min.z;
  if (depth > thickness) rotor.scale(1, 1, thickness / depth);
  if (axis === "x") rotor.rotateY(Math.PI / 2);
  if (axis === "y") rotor.rotateX(Math.PI / 2);
  return rotor;
}

/** Square fan frame with a round opening, built along Z. */
export function fanFrame(size: number, thickness: number, detail: Detail): THREE.BufferGeometry {
  const s = size / 2;
  const corner = size * 0.08;
  const shape = new THREE.Shape();
  shape.moveTo(-s + corner, -s);
  shape.lineTo(s - corner, -s);
  shape.quadraticCurveTo(s, -s, s, -s + corner);
  shape.lineTo(s, s - corner);
  shape.quadraticCurveTo(s, s, s - corner, s);
  shape.lineTo(-s + corner, s);
  shape.quadraticCurveTo(-s, s, -s, s - corner);
  shape.lineTo(-s, -s + corner);
  shape.quadraticCurveTo(-s, -s, -s + corner, -s);
  shape.holes.push(new THREE.Path().absarc(0, 0, s - 3, 0, Math.PI * 2, true));
  const bevel = 0.8;
  const g = new THREE.ExtrudeGeometry(shape, {
    // Bevels add to both faces: extrude less so the total equals the real frame thickness.
    depth: thickness - 2 * bevel,
    bevelEnabled: true,
    bevelThickness: bevel,
    bevelSize: bevel,
    bevelSegments: 1,
    curveSegments: detail === "high" ? 24 : 12,
  });
  g.translate(0, 0, -(thickness - 2 * bevel) / 2);
  return clean(g);
}

/** Stack of thin parallel fins. Fins are normal to `stackAxis`; the stack spans `size` on each axis. */
export function finStack(size: [number, number, number], stackAxis: Axis, pitch: number, finThickness: number): THREE.BufferGeometry {
  const [w, h, d] = size;
  const length = stackAxis === "x" ? w : stackAxis === "y" ? h : d;
  const count = Math.max(2, Math.floor(length / pitch));
  const step = length / count;
  const fins: THREE.BufferGeometry[] = [];
  for (let i = 0; i < count; i++) {
    const offset = -length / 2 + step * (i + 0.5);
    if (stackAxis === "x") fins.push(at(box(finThickness, h, d), offset, 0, 0));
    if (stackAxis === "y") fins.push(at(box(w, finThickness, d), 0, offset, 0));
    if (stackAxis === "z") fins.push(at(box(w, h, finThickness), 0, 0, offset));
  }
  return merge(fins);
}

/** U-shaped heat pipes rising from a cooler base (+X), spread across Y, legs offset in Z. */
export function heatPipes(height: number, spreadY: number, spreadZ: number, count: number, detail: Detail): THREE.BufferGeometry {
  const pipes: THREE.BufferGeometry[] = [];
  const radius = 3;
  for (let i = 0; i < count; i++) {
    const y = count === 1 ? 0 : -spreadY / 2 + (spreadY * i) / (count - 1);
    const curve = new THREE.CatmullRomCurve3([
      new THREE.Vector3(height / 2, y, -spreadZ / 2),
      new THREE.Vector3(-height / 2 + 6, y, -spreadZ / 3),
      new THREE.Vector3(-height / 2, y, 0),
      new THREE.Vector3(-height / 2 + 6, y, spreadZ / 3),
      new THREE.Vector3(height / 2, y, spreadZ / 2),
    ]);
    pipes.push(new THREE.TubeGeometry(curve, detail === "high" ? 32 : 14, radius, detail === "high" ? 10 : 6, false));
  }
  return merge(pipes);
}

/** Row of evenly spaced rectangular strips (expansion slot covers, vents). Strips stack along `axis`. */
export function strips(size: [number, number, number], axis: Axis, count: number, gapRatio: number): THREE.BufferGeometry {
  const [w, h, d] = size;
  const length = axis === "x" ? w : axis === "y" ? h : d;
  const step = length / count;
  const strip = step * (1 - gapRatio);
  const pieces: THREE.BufferGeometry[] = [];
  for (let i = 0; i < count; i++) {
    const o = -length / 2 + step * (i + 0.5);
    if (axis === "x") pieces.push(at(box(strip, h, d), o, 0, 0));
    if (axis === "y") pieces.push(at(box(w, strip, d), 0, o, 0));
    if (axis === "z") pieces.push(at(box(w, h, strip), 0, 0, o));
  }
  return merge(pieces);
}

/** Grid of small bumps on a plate face (connector pins, perforation hints). Plate normal along `axis`. */
export function studGrid(width: number, height: number, cols: number, rows: number, stud: number, depth: number, axis: Axis): THREE.BufferGeometry {
  const pieces: THREE.BufferGeometry[] = [];
  for (let c = 0; c < cols; c++) {
    for (let r = 0; r < rows; r++) {
      const u = -width / 2 + (width * (c + 0.5)) / cols;
      const v = -height / 2 + (height * (r + 0.5)) / rows;
      if (axis === "z") pieces.push(at(box(stud, stud, depth), u, v, 0));
      if (axis === "x") pieces.push(at(box(depth, stud, stud), 0, v, u));
      if (axis === "y") pieces.push(at(box(stud, depth, stud), u, 0, v));
    }
  }
  return merge(pieces);
}

export function triangleCount(geometry: THREE.BufferGeometry): number {
  return geometry.index ? geometry.index.count / 3 : geometry.getAttribute("position").count / 3;
}
