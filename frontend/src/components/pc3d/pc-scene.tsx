"use client";

import { ContactShadows, Environment, OrbitControls, useGLTF } from "@react-three/drei";
import { Canvas, useFrame, useThree, type ThreeEvent } from "@react-three/fiber";
import { Component, Suspense, useEffect, useMemo, useRef, type ReactNode } from "react";
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
  // Stable per assembly: the camera only re-frames when the build or the selected part changes, never on a re-render.
  const { center, distance } = useMemo(() => {
    const caseBox = assembly.placements.find((p) => p.key === "case")?.box;
    const center = caseBox ? caseBox.getCenter(new THREE.Vector3()) : new THREE.Vector3(0, 0.24, 0);
    const size = caseBox ? caseBox.getSize(new THREE.Vector3()) : new THREE.Vector3(0.24, 0.48, 0.46);
    return { center, distance: Math.max(size.x, size.y, size.z) * 2.1 };
  }, [assembly]);
  const focusBox = useMemo(() => {
    if (selectedKey === null) return null;
    // Memory sticks are separate placements; frame all of them together.
    const parts = assembly.placements.filter((p) =>
      selectedKey.startsWith("memory") ? p.category === "MEMORY" : p.key === selectedKey,
    );
    return parts.length ? parts.reduce((box, p) => box.union(p.box), new THREE.Box3()) : null;
  }, [assembly, selectedKey]);

  return (
    <Canvas
      shadows
      dpr={[1, 2]}
      camera={{ position: [center.x + distance * 0.95, center.y + distance * 0.35, center.z + distance * 0.55], fov: 35, near: 0.01, far: 20 }}
      onPointerMissed={() => onSelect(null)}
    >
      <color attach="background" args={["#1e1e27"]} />
      <StudioEnvironment />
      <hemisphereLight args={["#ffffff", "#3a3548", 0.75]} />
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
      <OrbitControls makeDefault target={center} enableDamping minDistance={0.1} maxDistance={3} />
      <CameraFocus box={focusBox} overviewTarget={center} overviewDistance={distance} />
      {onStats && <StatsReporter onStats={onStats} />}
    </Canvas>
  );
}

/** Same angle as the opening view: from the open side of the case, slightly above and in front. */
const VIEW_DIRECTION = new THREE.Vector3(0.95, 0.35, 0.55).normalize();

/**
 * Glides the camera to frame the selected part (or back to the whole PC when nothing is selected).
 * Grabbing the scene with the mouse stops the glide, so orbiting always stays in the user's hands.
 */
function CameraFocus({ box, overviewTarget, overviewDistance }: { box: THREE.Box3 | null; overviewTarget: THREE.Vector3; overviewDistance: number }) {
  const camera = useThree((state) => state.camera) as THREE.PerspectiveCamera;
  const controls = useThree((state) => state.controls) as unknown as
    | (THREE.EventDispatcher<{ start: object }> & { target: THREE.Vector3; update: () => void })
    | null;
  const goal = useRef<{ target: THREE.Vector3; position: THREE.Vector3 } | null>(null);

  useEffect(() => {
    const target = box ? box.getCenter(new THREE.Vector3()) : overviewTarget.clone();
    let distance = overviewDistance;
    if (box) {
      // Distance at which the part's bounding sphere fills about half of the view.
      const radius = box.getBoundingSphere(new THREE.Sphere()).radius;
      distance = Math.max(0.16, (radius / Math.sin(THREE.MathUtils.degToRad(camera.fov / 2))) * 1.5);
    }
    goal.current = { target, position: target.clone().addScaledVector(VIEW_DIRECTION, distance) };
  }, [box, overviewTarget, overviewDistance, camera]);

  useEffect(() => {
    if (!controls) return;
    const stop = () => (goal.current = null);
    controls.addEventListener("start", stop);
    return () => controls.removeEventListener("start", stop);
  }, [controls]);

  useFrame((_, delta) => {
    if (!goal.current || !controls) return;
    const t = 1 - Math.exp(-delta * 5);
    controls.target.lerp(goal.current.target, t);
    camera.position.lerp(goal.current.position, t);
    controls.update();
    if (camera.position.distanceTo(goal.current.position) < 0.001 && controls.target.distanceTo(goal.current.target) < 0.001) {
      goal.current = null;
    }
  });

  return null;
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
        standard.emissive.set(selected ? "#9333ea" : "#000000");
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
  const gl = useThree((state) => state.gl);
  const texture = useMemo(() => {
    const pmrem = new THREE.PMREMGenerator(gl);
    const map = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    pmrem.dispose();
    return map;
  }, [gl]);
  useEffect(() => () => texture.dispose(), [texture]);
  return <Environment map={texture} environmentIntensity={0.9} />;
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
