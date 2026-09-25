// Turns a procedurally built model into an optimised GLB.
//
// Node contract (what the runtime relies on):
//   <modelId>                    root, extras: { modelId, family, variant, units, baseParams }
//     <part>                     one per layout part; translation = part centre; extras: { part, baseSize (mm) }
//       <part>__mesh             geometry (quantisation may add its own transform here, never on <part>)
//     mount_<name>               empty node marking an attachment point
// Geometry is authored in millimetres and written in metres (glTF units).

import * as THREE from "three";
import { Document, Logger, NodeIO, type Material } from "@gltf-transform/core";
import { EXTMeshoptCompression, KHRMaterialsEmissiveStrength, KHRMeshQuantization } from "@gltf-transform/extensions";
import { dedup, meshopt, prune, weld } from "@gltf-transform/functions";
import { MeshoptEncoder } from "meshoptimizer";
import type { Layout } from "../../../frontend/src/lib/models3d/layout.ts";
import { MATERIALS, type MaterialKey } from "./materials.ts";
import { triangleCount } from "./geometry.ts";

export interface Primitive {
  geometry: THREE.BufferGeometry;
  material: MaterialKey;
}

export interface ModelDraft {
  modelId: string;
  family: string;
  variant: string;
  baseParams: Record<string, unknown>;
  layout: Layout;
  /** Geometry per layout part, authored centred and at the layout size. */
  parts: Record<string, Primitive[]>;
}

export interface ExportResult {
  bytes: Uint8Array;
  triangles: number;
  materials: number;
}

const MM = 0.001;

export async function exportGlb(draft: ModelDraft): Promise<ExportResult> {
  await MeshoptEncoder.ready;
  const doc = new Document().setLogger(new Logger(Logger.Verbosity.WARN));
  const emissive = doc.createExtension(KHRMaterialsEmissiveStrength);
  const buffer = doc.createBuffer();
  const scene = doc.createScene(draft.modelId);
  const root = doc.createNode(draft.modelId).setExtras({
    modelId: draft.modelId,
    family: draft.family,
    variant: draft.variant,
    units: "m",
    authoredIn: "mm",
    baseParams: draft.baseParams,
  });
  scene.addChild(root);

  const materials = new Map<MaterialKey, Material>();
  const material = (key: MaterialKey) => {
    let m = materials.get(key);
    if (m) return m;
    const spec = MATERIALS[key];
    m = doc.createMaterial(spec.name)
      .setBaseColorFactor(spec.baseColor)
      .setMetallicFactor(spec.metallic)
      .setRoughnessFactor(spec.roughness)
      .setDoubleSided(Boolean(spec.doubleSided));
    if (spec.alphaBlend) m.setAlphaMode("BLEND");
    if (spec.emissive) {
      m.setEmissiveFactor(spec.emissive);
      m.setExtension("KHR_materials_emissive_strength", emissive.createEmissiveStrength().setEmissiveStrength(2));
    }
    m.setExtras({ role: key });
    materials.set(key, m);
    return m;
  };

  let triangles = 0;
  for (const [name, layoutPart] of Object.entries(draft.layout.parts)) {
    const primitives = draft.parts[name];
    if (!primitives || primitives.length === 0) continue;
    const mesh = doc.createMesh(name);
    for (const primitive of primitives) {
      const g = primitive.geometry.index ? primitive.geometry.toNonIndexed() : primitive.geometry;
      const positions = (g.getAttribute("position").array as Float32Array).map((v) => v * MM);
      const normals = g.getAttribute("normal").array as Float32Array;
      triangles += triangleCount(g);
      const prim = doc.createPrimitive()
        .setAttribute("POSITION", doc.createAccessor().setType("VEC3").setArray(new Float32Array(positions)).setBuffer(buffer))
        .setAttribute("NORMAL", doc.createAccessor().setType("VEC3").setArray(new Float32Array(normals)).setBuffer(buffer))
        .setMaterial(material(primitive.material));
      mesh.addPrimitive(prim);
    }
    const holder = doc.createNode(`${name}__mesh`).setMesh(mesh);
    const node = doc.createNode(name)
      .setTranslation(layoutPart.position.map((v) => v * MM) as [number, number, number])
      .setExtras({ part: name, baseSize: layoutPart.size, visible: layoutPart.visible !== false })
      .addChild(holder);
    root.addChild(node);
  }

  for (const [name, mount] of Object.entries(draft.layout.mounts)) {
    const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(...(mount.rotation ?? [0, 0, 0])));
    root.addChild(doc.createNode(`mount_${name}`)
      .setTranslation(mount.position.map((v) => v * MM) as [number, number, number])
      .setRotation([q.x, q.y, q.z, q.w])
      .setExtras({ mount: name }));
  }

  await doc.transform(
    dedup(),
    weld(),
    prune({ keepLeaves: true, keepAttributes: false, keepExtras: true }),
    // Reorders, quantises (per mesh, so part node transforms stay clean) and compresses.
    meshopt({ encoder: MeshoptEncoder, level: "medium", quantizationVolume: "mesh" }),
  );
  const bytes = await new NodeIO()
    .registerExtensions([EXTMeshoptCompression, KHRMeshQuantization, KHRMaterialsEmissiveStrength])
    .registerDependencies({ "meshopt.encoder": MeshoptEncoder })
    .writeBinary(doc);
  return { bytes, triangles, materials: materials.size };
}
