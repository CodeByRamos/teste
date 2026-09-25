// Adapts a loaded base GLB to one component. Parts carry their authored size in extras (`baseSize`); each
// part is scaled to the size the layout asks for, so proportions are decided by layout.ts, not by stretching
// the whole model.

import * as THREE from "three";
import type { Layout } from "./layout";

const MM = 0.001;

export function applyLayout(model: THREE.Object3D, layout: Layout): void {
  model.traverse((node) => {
    const data = node.userData as { part?: string; baseSize?: [number, number, number]; mount?: string };
    if (data.part && data.baseSize) {
      const target = layout.parts[data.part];
      if (!target) {
        node.visible = false;
        return;
      }
      node.position.set(target.position[0] * MM, target.position[1] * MM, target.position[2] * MM);
      node.scale.set(
        ratio(target.size[0], data.baseSize[0]),
        ratio(target.size[1], data.baseSize[1]),
        ratio(target.size[2], data.baseSize[2]),
      );
      node.visible = target.visible !== false;
    }
    if (data.mount) {
      const mount = layout.mounts[data.mount];
      if (mount) node.position.set(mount.position[0] * MM, mount.position[1] * MM, mount.position[2] * MM);
    }
  });
}

function ratio(target: number, base: number) {
  return base > 0 ? target / base : 1;
}

/** Gives the instance its own materials, recoloured by role. */
export function applyMaterials(model: THREE.Object3D, options: { color: string | null; rgb: boolean; lighting?: string }): void {
  const cache = new Map<THREE.Material, THREE.Material>();
  model.traverse((node) => {
    const mesh = node as THREE.Mesh;
    if (!mesh.isMesh) return;
    const swap = (material: THREE.Material) => {
      let copy = cache.get(material);
      if (!copy) {
        copy = material.clone();
        const role = (material.userData as { role?: string }).role ?? material.name;
        const standard = copy as THREE.MeshStandardMaterial;
        if (role === "body" && options.color && standard.color) standard.color.set(options.color);
        if (role === "rgb" && standard.emissive) {
          standard.emissive.set(options.lighting ?? "#6f8cff");
          standard.emissiveIntensity = options.rgb ? 2 : 0;
        }
        cache.set(material, copy);
      }
      return copy;
    };
    mesh.material = Array.isArray(mesh.material) ? mesh.material.map(swap) : swap(mesh.material);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
  });
}

/** Maps catalogue colour names from the data source to display colours. */
export const COLOR_NAMES: Record<string, string> = {
  BLACK: "#1d1f22",
  WHITE: "#e9e9e6",
  GREY: "#7c8087",
  RED: "#9e2a2b",
  BLUE: "#2c4f8a",
  GREEN: "#2f6b45",
  PINK: "#d99bb3",
  GOLD: "#b8964c",
  PURPLE: "#5a3f86",
  YELLOW: "#c9a93b",
  ORANGE: "#c7652f",
  BROWN: "#6b4a33",
};
