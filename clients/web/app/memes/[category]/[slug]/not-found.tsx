import Link from "next/link";
import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { EmptyState } from "@/components/ui/EmptyState";

export default function MemeNotFound() {
  return (
    <>
      <Nav />
      <main id="contenido" style={{ paddingTop: "var(--nav-height)" }}>
        <div className="container" style={{ padding: "var(--space-12) 0" }}>
          <EmptyState
            title="Meme no encontrado"
            description="Capaz se borró, capaz nunca existió. Volvé al inicio o probá otra búsqueda."
            ctaLabel="Volver al inicio"
            ctaHref="/"
            glyph="🤷"
          />
          <p style={{ textAlign: "center", marginTop: "var(--space-4)" }}>
            <Link href="/buscar" style={{ color: "var(--accent-primary)" }}>
              o buscá otro meme →
            </Link>
          </p>
        </div>
      </main>
      <Footer />
    </>
  );
}
