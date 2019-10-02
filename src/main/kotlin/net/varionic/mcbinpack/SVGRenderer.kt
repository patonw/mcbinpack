package net.varionic.mcbinpack

class SVGRenderer : BaseRenderer<String>() {
    override fun render(bin: Bin): String {
        val bins = """<rect x="0" y="0" width="${bin.width}" height="${bin.height}" style="fill:white;stroke:black;stroke-width:2"/>"""
        val dividers = renderDividers(bin).joinToString("\n") {
            "<line x1=${it.start.x} y1=${it.start.y} x2=${it.end.x} y2=${it.end.y} style=\"stroke:blue;stroke-width:1\"/>"
        }

        val items = renderItems(bin).joinToString("\n") {
            """<rect x="${it.start.x}" y="${it.start.y}" width="${it.width}" height="${it.height}" style="fill:gray;stroke:black;stroke-width:1"/>"""
        }

        val vacancies = renderVacancies(bin).joinToString("\n") {
            """<rect x="${it.start.x}" y="${it.start.y}" width="${it.width}" height="${it.height}" style="fill:rgb(255, 255, 128)"/>"""
        }

        val rejects = renderRejects(bin).joinToString("\n") {
            """<rect x="${it.start.x}" y="${it.start.y}" width="${it.width}" height="${it.height}" style="fill:red"/>"""
        }

        return """
            <svg width="400" height="300">
              <g transform="translate(10 10)">
              $bins
              $vacancies
              $items
              $dividers
              $rejects
              </g>
              Sorry, your browser does not support inline SVG.
            </svg>
            """.trimIndent()
    }

}