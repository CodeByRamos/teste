"use client";

import { ContactShadows, OrbitControls, useGLTF } from "@react-three/drei";
import { Canvas, useThree, type ThreeEvent } from "@react-three/fiber";
import { Component, Suspense, useEffect, useMemo, type ReactNode } from "react";
import * as THREE from "three";
import { RoomEnvironment } from "three/examples/jsm/environments/RoomEnvironment.js";
import type { Assembly, Placement } from "@/lib/models3d/assembly";
import { applyLayout, applyMaterials } from "@/lib/models3d/rig";

export interface SceneStats {
  triangles: number;
  drawCalls: number;
  geometries: number;
}

export function PcScene({
  assembly,
  urls,
  selectedKey,
  onSelect,
  hideSidePanel,
  onStats,
}: {
  assembly: Assembly;
  /** modelId → GLB url (high or low detail). */
  urls: Record<string, string>;
  selectedKey: string | null;
  onSelect: (placement: Placement | null) => void;
  hideSidePanel: boolean;
  onStats?: (stats: SceneStats) => void;
}) {
  const caseBox = assembly.placements.find((p) => p.key === "case")?.box;
  const center = caseBox ? caseBox.getCenter(new THREE.Vector3()) : new THREE.Vector3(0, 0.24, 0);
  const size = caseBox ? caseBox.getSize(new THREE.Vector3()) : new THREE.Vector3(0.24, 0.48, 0.46);
  const distance = Math.max(size.x, size.y, size.z) * 2.1;

  return (
    <Canvas
      shadows
      dpr={[1, 2]}
      camera={{ position: [center.x + distance * 0.95, center.y + distance * 0.35, center.z + distance * 0.55], fov: 35, near: 0.01, far: 20 }}
      onPointerMissed={() => onSelect(null)}
    >
      <color attach="background" args={["#f4f3ef"]} />
      <StudioEnvironment />
      <hemisphereLight args={["#ffffff", "#d8d4cc", 0.6]} />
      <directionalLight position={[1.2, 1.6, 0.8]} intensity={1.8} castShadow shadow-mapSize={[2048, 2048]} shadow-bias={-0.0004} />
      <directionalLight position={[-1, 0.8, -0.6]} intensity={0.6} />
      <Suspense fallback={null}>
        {assembly.placements.map((placement) => (
          <PlacedModel
            key={placement.key}
            placement={placement}
            url={urls[placement.spec.modelId]}
            selected={placement.key === selectedKey || (selectedKey !== null && placement.category === "MEMORY" && selectedKey.startsWith("memory"))}
            onSelect={onSelect}
            hideSidePanel={hideSidePanel}
          />
        ))}
        {assembly.tubes.map((tube, index) => (
          <TubeMesh key={index} from={tube.from} to={tube.to} />
        ))}
      </Suspense>
      <ContactShadows position={[center.x, 0, center.z]} scale={distance * 1.2} blur={2.4} opacity={0.35} far={1} />
      <OrbitControls makeDefault target={center} enableDamping minDistance={0.25} maxDistance={3} />
      {onStats && <StatsReporter onStats={onStats} />}
    </Canvas>
  );
}

function PlacedModel({
  placement,
  url,
  selected,
  onSelect,
  hideSidePanel,
}: {
  placement: Placement;
  url: string | undefined;
  selected: boolean;
  onSelect: (placement: Placement) => void;
  hideSidePanel: boolean;
}) {
  if (!url) return null;
  // One missing or broken model must not take the whole scene down.
  return (
    <ModelErrorBoundary>
      <LoadedModel placement={placement} url={url} selected={selected} onSelect={onSelect} hideSidePanel={hideSidePanel} />
    </ModelErrorBoundary>
  );
}

class ModelErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? null : this.props.children;
  }
}

function LoadedModel({
  placement,
  url,
  selected,
  onSelect,
  hideSidePanel,
}: {
  placement: Placement;
  url: string;
  selected: boolean;
  onSelect: (placement: Placement) => void;
  hideSidePanel: boolean;
}) {
  const gltf = useGLTF(url, false, true);
  const object = useMemo(() => {
    const copy = gltf.scene.clone(true);
    applyLayout(copy, placement.layout);
    applyMaterials(copy, { color: placement.spec.color, rgb: placement.spec.rgb });
    return copy;
  }, [gltf, placement]);

  useEffect(() => {
    object.traverse((node) => {
      if ((node.userData as { part?: string }).part === "side_panel") node.visible = !hideSidePanel;
    });
  }, [object, hideSidePanel]);

  useEffect(() => {
    // Selection glow: a faint emissive tint on the whole part.
    object.traverse((node) => {
      const mesh = node as THREE.Mesh;
      if (!mesh.isMesh) return;
      for (const material of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
        const standard = material as THREE.MeshStandardMaterial;
        if (!standard.emissive || (material.userData as { role?: string }).role === "rgb") continue;
        standard.emissive.set(selected ? "#3552c7" : "#000000");
        standard.emissiveIntensity = selected ? 0.35 : 0;
      }
    });
  }, [object, selected]);

  const click = (event: ThreeEvent<MouseEvent>) => {
    if (placement.category === "CASE" && !event.object.visible) return;
    event.stopPropagation();
    onSelect(placement);
  };

  return (
    <group matrixAutoUpdate={false} matrix={placement.matrix} onClick={click}>
      <primitive object={object} />
    </group>
  );
}

function TubeMesh({ from, to }: { from: THREE.Vector3; to: THREE.Vector3 }) {
  const geometry = useMemo(() => {
    const lift = new THREE.Vector3(0.04, 0, 0);
    const mid = from.clone().lerp(to, 0.5).add(new THREE.Vector3(0.05, 0.02, 0));
    const curve = new THREE.CatmullRomCurve3([from, from.clone().add(lift), mid, to.clone().add(new THREE.Vector3(0.01, -0.03, 0)), to]);
    return new THREE.TubeGeometry(curve, 48, 0.0055, 10, false);
  }, [from, to]);
  return (
    <mesh geometry={geometry} castShadow>
      <meshStandardMaterial color="#141516" roughness={0.6} />
    </mesh>
  );
}

/** Image-based lighting generated on the GPU from a procedural room (no HDR download). Metals need it to read. */
function StudioEnvironment() {
  const { gl, scene } = useThree();
  useEffect(() => {
    const pmrem = new THREE.PMREMGenerator(gl);
    const texture = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    scene.environment = texture;
    scene.environmentIntensity = 0.9;
    return () => {
      scene.environment = null;
      texture.dispose();
      pmrem.dispose();
    };
  }, [gl, scene]);
  return null;
}

function StatsReporter({ onStats }: { onStats: (stats: SceneStats) => void }) {
  const gl = useThree((state) => state.gl);
  useEffect(() => {
    const timer = setInterval(() => {
      onStats({ triangles: gl.info.render.triangles, drawCalls: gl.info.render.calls, geometries: gl.info.memory.geometries });
    }, 1000);
    return () => clearInterval(timer);
  }, [gl, onStats]);
  return null;
}
