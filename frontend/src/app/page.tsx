import Link from "next/link";
import { brand } from "@/config/brand";
import { IntakeBox } from "@/components/intake-box";
import { ArrowIcon, BoxIcon, CalendarIcon, MonitorIcon, WrenchIcon } from "@/components/icons";

const actions = [
  {
    href: "/montar",
    title: "Montar meu PC",
    description: "Conte o que você quer fazer e quanto quer gastar. Nós escolhemos as peças.",
    Icon: BoxIcon,
    available: true,
  },
  {
    href: "/verificar",
    title: "Tenho um PC",
    description: "Informe as peças que você tem e veja se está tudo compatível.",
    Icon: MonitorIcon,
    available: true,
  },
  {
    href: "/melhorar",
    title: "Quero melhorar meu PC",
    description: "Descubra o que trocar primeiro e o que mais precisa mudar junto.",
    Icon: WrenchIcon,
    available: true,
  },
  {
    href: null,
    title: "Planejar meu próximo upgrade",
    description: "Compre hoje pensando nos upgrades dos próximos anos.",
    Icon: CalendarIcon,
    available: false,
  },
];

const steps = [
  {
    title: "Você diz o que precisa",
    text: "Jogar, programar, editar vídeos, estudar — e quanto quer investir. Sem precisar saber o nome de nenhuma peça.",
  },
  {
    title: "Nós transformamos em peças",
    text: "Cada uso vira requisitos técnicos. Comparamos milhares de componentes e escolhemos o melhor equilíbrio para o seu orçamento.",
  },
  {
    title: "Verificamos e explicamos tudo",
    text: "Encaixes, espaço no gabinete, energia da fonte: tudo checado por regras, não por palpite. E cada escolha vem com o porquê.",
  },
];

export default function Home() {
  return (
    <>
      <section className="mx-auto max-w-4xl px-4 pt-16 pb-12 sm:px-6 sm:pt-24">
        <h1 className="text-center text-4xl font-semibold tracking-tight text-balance sm:text-6xl">{brand.tagline}</h1>
        <p className="mx-auto mt-5 max-w-2xl text-center text-lg leading-relaxed text-muted text-pretty">{brand.description}</p>
        <div className="mt-10">
          <IntakeBox />
        </div>
      </section>

      <section aria-labelledby="comecar" className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <h2 id="comecar" className="sr-only">
          Por onde começar
        </h2>
        <ul className="grid gap-px overflow-hidden rounded-2xl border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
          {actions.map(({ href, title, description, Icon, available }) => {
            const content = (
              <>
                <Icon className={`size-6 ${available ? "text-accent" : "text-subtle"}`} />
                <span className="mt-4 flex items-center gap-2 font-semibold">
                  {title}
                  {!available && (
                    <span className="rounded-full bg-surface-muted px-2 py-0.5 text-xs font-medium text-muted">Em breve</span>
                  )}
                </span>
                <span className="mt-1.5 text-sm leading-relaxed text-muted">{description}</span>
                {available && (
                  <span className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-accent">
                    Começar <ArrowIcon className="size-4 transition-transform group-hover:translate-x-0.5" />
                  </span>
                )}
              </>
            );
            return (
              <li key={title} className="bg-surface">
                {href ? (
                  <Link href={href} className="group flex h-full flex-col p-6 transition-colors hover:bg-accent-soft/40">
                    {content}
                  </Link>
                ) : (
                  <div className="flex h-full flex-col p-6" aria-disabled>
                    {content}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      </section>

      <section aria-labelledby="como-funciona" className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
        <h2 id="como-funciona" className="text-2xl font-semibold tracking-tight">
          Como funciona
        </h2>
        <ol className="mt-8 grid gap-10 md:grid-cols-3">
          {steps.map((step, index) => (
            <li key={step.title}>
              <span className="font-mono text-sm text-accent">0{index + 1}</span>
              <h3 className="mt-2 text-lg font-semibold">{step.title}</h3>
              <p className="mt-2 leading-relaxed text-muted">{step.text}</p>
            </li>
          ))}
        </ol>
      </section>
    </>
  );
}
