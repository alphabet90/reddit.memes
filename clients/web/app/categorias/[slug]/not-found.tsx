import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { EmptyState } from "@/components/ui/EmptyState";

export default function CategoryNotFound() {
  return (
    <>
      <Nav />
      <main id="contenido" style={{ paddingTop: "var(--nav-height)" }}>
        <div className="container" style={{ padding: "var(--space-12) 0" }}>
          <EmptyState
            title="Categoría no encontrada"
            description="Esa categoría no existe (todavía). Mirá las que sí están."
            ctaLabel="Ver categorías"
            ctaHref="/categorias"
            glyph="🗂"
          />
        </div>
      </main>
      <Footer />
    </>
  );
}
