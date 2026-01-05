package com.example.collage

data class RectFNorm(val x: Float, val y: Float, val w: Float, val h: Float)

data class CollageTemplate(
    val id: String,
    val name: String,
    val slots: List<RectFNorm>
)


object CollageTemplates {
    // Instagram-friendly square templates (1:1)
    val all: List<CollageTemplate> = listOf(
        // 1
        CollageTemplate(
            id = "solo",
            name = "Solo",
            slots = listOf(RectFNorm(0f, 0f, 1f, 1f))
        ),
        // 2
        CollageTemplate(
            id = "two_vertical",
            name = "Split 2",
            slots = listOf(
                RectFNorm(0f, 0f, 0.5f, 1f),
                RectFNorm(0.5f, 0f, 0.5f, 1f)
            )
        ),
        CollageTemplate(
            id = "two_horizontal",
            name = "Stack 2",
            slots = listOf(
                RectFNorm(0f, 0f, 1f, 0.5f),
                RectFNorm(0f, 0.5f, 1f, 0.5f)
            )
        ),
        // 3
        CollageTemplate(
            id = "three_cols",
            name = "3 Columns",
            slots = listOf(
                RectFNorm(0f, 0f, 1f/3f, 1f),
                RectFNorm(1f/3f, 0f, 1f/3f, 1f),
                RectFNorm(2f/3f, 0f, 1f/3f, 1f)
            )
        ),
        CollageTemplate(
            id = "three_rows",
            name = "3 Rows",
            slots = listOf(
                RectFNorm(0f, 0f, 1f, 1f/3f),
                RectFNorm(0f, 1f/3f, 1f, 1f/3f),
                RectFNorm(0f, 2f/3f, 1f, 1f/3f)
            )
        ),
        // 4
        CollageTemplate(
            id = "grid_2x2",
            name = "Grid 2×2",
            slots = listOf(
                RectFNorm(0f, 0f, 0.5f, 0.5f),
                RectFNorm(0.5f, 0f, 0.5f, 0.5f),
                RectFNorm(0f, 0.5f, 0.5f, 0.5f),
                RectFNorm(0.5f, 0.5f, 0.5f, 0.5f)
            )
        ),
        // Hero layouts
        CollageTemplate(
            id = "hero_two",
            name = "Hero + 2",
            slots = listOf(
                RectFNorm(0f, 0f, 0.65f, 1f),
                RectFNorm(0.65f, 0f, 0.35f, 0.5f),
                RectFNorm(0.65f, 0.5f, 0.35f, 0.5f)
            )
        ),
        CollageTemplate(
            id = "hero_three",
            name = "Hero + 3",
            slots = listOf(
                RectFNorm(0f, 0f, 0.60f, 1f),
                RectFNorm(0.60f, 0f, 0.40f, 1f/3f),
                RectFNorm(0.60f, 1f/3f, 0.40f, 1f/3f),
                RectFNorm(0.60f, 2f/3f, 0.40f, 1f/3f)
            )
        ),
        // 6
        CollageTemplate(
            id = "grid_2x3",
            name = "Grid 2×3",
            slots = listOf(
                RectFNorm(0f, 0f, 1f/3f, 0.5f),
                RectFNorm(1f/3f, 0f, 1f/3f, 0.5f),
                RectFNorm(2f/3f, 0f, 1f/3f, 0.5f),
                RectFNorm(0f, 0.5f, 1f/3f, 0.5f),
                RectFNorm(1f/3f, 0.5f, 1f/3f, 0.5f),
                RectFNorm(2f/3f, 0.5f, 1f/3f, 0.5f)
            )
        ),
        // Mosaic
        CollageTemplate(
            id = "mosaic",
            name = "Mosaic",
            slots = listOf(
                RectFNorm(0f, 0f, 0.5f, 0.62f),
                RectFNorm(0.5f, 0f, 0.5f, 0.38f),
                RectFNorm(0.5f, 0.38f, 0.5f, 0.62f),
                RectFNorm(0f, 0.62f, 0.5f, 0.38f),
                RectFNorm(0.5f, 0.62f, 0.5f, 0.38f)
            )
        )
    )
}
