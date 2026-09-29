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
          background: "radial-gradient(circle at 85% 0%, #3b1a78 0%, #1c1530 45%, #17171e 80%)",
          color: "#f2f1f7",
        }}
      >
        <div style={{ width: 88, height: 88, borderRadius: 22, background: "linear-gradient(135deg, #5b21b6 0%, #7e22ce 50%, #c026d3 100%)", marginBottom: 48, display: "flex" }} />
        <div style={{ fontSize: 76, fontWeight: 700, letterSpacing: -2, lineHeight: 1.05 }}>{brand.name}</div>
        <div style={{ fontSize: 40, color: "#b9b7c6", marginTop: 24, lineHeight: 1.3, maxWidth: 900 }}>{brand.tagline}</div>
      </div>
    ),
    size,
  );
}
