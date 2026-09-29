import { ImageResponse } from "next/og";
import { brand } from "@/config/brand";

// Social preview (WhatsApp, redes sociais). Brand-neutral: name and tagline come from the brand config.
export const alt = brand.name;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "80px",
          background: "#141217",
          color: "#f4f3f7",
        }}
      >
        <div style={{ width: 88, height: 88, borderRadius: 22, background: "#7e22ce", marginBottom: 48, display: "flex" }} />
        <div style={{ fontSize: 76, fontWeight: 700, letterSpacing: -2, lineHeight: 1.05 }}>{brand.name}</div>
        <div style={{ fontSize: 40, color: "#c0bac9", marginTop: 24, lineHeight: 1.3, maxWidth: 900 }}>{brand.tagline}</div>
      </div>
    ),
    size,
  );
}
