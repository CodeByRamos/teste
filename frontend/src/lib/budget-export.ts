import type { BuildItem, BuildView } from "./types";

/** One line of the exported budget: part type, name, price and share of the total. */
interface Line {
  category: string;
  name: string;
  price: number;
  share: number;
  owned: boolean;
}

function cost(item: BuildItem) {
  return item.owned ? 0 : (item.price?.amountBrl ?? 0);
}

function lines(build: BuildView): Line[] {
  const total = build.totals.totalBrl || 1;
  return [...build.items]
    .sort((a, b) => cost(b) - cost(a))
    .map((item) => ({
      category: item.categoryLabel,
      name: item.component.name,
      price: cost(item),
      share: cost(item) / total,
      owned: item.owned,
    }));
}

const money = (value: number) => new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
const today = () => new Date().toISOString().slice(0, 10);

/** File name like "orcamento-pc-2026-09-29". */
function fileName(extension: string) {
  return `orcamento-pc-${today()}.${extension}`;
}

/** The budget as a PDF: title, the parts table and the totals, with the fictitious-price note when it applies. */
export async function exportBudgetPdf(build: BuildView, title: string) {
  const [{ jsPDF }, { autoTable }] = await Promise.all([import("jspdf"), import("jspdf-autotable")]);
  const doc = new jsPDF({ unit: "pt", format: "a4" });
  const violet: [number, number, number] = [126, 34, 206];

  doc.setFont("helvetica", "bold");
  doc.setFontSize(18);
  doc.text("Orçamento do PC", 40, 56);
  doc.setFont("helvetica", "normal");
  doc.setFontSize(11);
  doc.setTextColor(90);
  doc.text(title, 40, 76);
  doc.text(`Gerado em ${new Date().toLocaleDateString("pt-BR")}`, 40, 92);

  autoTable(doc, {
    startY: 110,
    head: [["Peça", "Modelo", "Preço", "% do total"]],
    body: lines(build).map((line) => [
      line.category,
      line.name,
      line.owned ? "Você já tem" : money(line.price),
      line.owned ? "-" : `${Math.round(line.share * 100)}%`,
    ]),
    headStyles: { fillColor: violet, textColor: 255 },
    styles: { fontSize: 9, cellPadding: 6 },
    columnStyles: { 2: { halign: "right", cellWidth: 80 }, 3: { halign: "right", cellWidth: 60 } },
  });

  const { totals } = build;
  const endY = (doc as unknown as { lastAutoTable: { finalY: number } }).lastAutoTable.finalY + 24;
  doc.setTextColor(30);
  doc.setFont("helvetica", "bold");
  doc.text(`Total: ${money(totals.totalBrl)}`, 40, endY);
  doc.setFont("helvetica", "normal");
  if (totals.budgetBrl != null) {
    const left = totals.budgetBrl - totals.totalBrl;
    doc.text(`Orçamento: ${money(totals.budgetBrl)}   ${left >= 0 ? "Sobra" : "Acima"}: ${money(Math.abs(left))}`, 40, endY + 16);
  }
  if (totals.pricesAreExamples) {
    doc.setFontSize(9);
    doc.setTextColor(120);
    doc.text("Preços fictícios: ainda não há lojas conectadas.", 40, endY + 36);
  }
  doc.save(fileName("pdf"));
}

/** The budget as an Excel workbook: the parts table with real numbers (so it sums and sorts) and the totals. */
export async function exportBudgetXlsx(build: BuildView, title: string) {
  const { default: writeXlsxFile } = await import("write-excel-file/browser");
  type SheetData = import("write-excel-file/browser").SheetData;
  const bold = { fontWeight: "bold" as const };
  const currency = "R$ #,##0.00";
  const header = ["Peça", "Modelo", "Preço (R$)", "% do total"].map((value) => ({ value, ...bold }));
  const rows = lines(build).map((line) => [
    { value: line.category },
    { value: line.name },
    line.owned ? { value: "Você já tem" } : { value: line.price, type: Number, format: currency },
    line.owned ? { value: "-" } : { value: line.share, type: Number, format: "0%" },
  ]);
  const { totals } = build;
  const footer = [
    [],
    [{ value: "Total", ...bold }, null, { value: totals.totalBrl, type: Number, format: currency, ...bold }],
    ...(totals.budgetBrl != null
      ? [
          [{ value: "Orçamento" }, null, { value: totals.budgetBrl, type: Number, format: currency }],
          [{ value: totals.budgetBrl >= totals.totalBrl ? "Sobra" : "Acima" }, null, { value: Math.abs(totals.budgetBrl - totals.totalBrl), type: Number, format: currency }],
        ]
      : []),
    ...(totals.pricesAreExamples ? [[], [{ value: "Preços fictícios: ainda não há lojas conectadas." }]] : []),
  ];
  const data = [[{ value: title, ...bold }], [], header, ...rows, ...footer];
  await writeXlsxFile(data as unknown as SheetData, {
    sheet: "Orçamento",
    columns: [{ width: 22 }, { width: 60 }, { width: 16 }, { width: 12 }],
  }).toFile(fileName("xlsx"));
}
