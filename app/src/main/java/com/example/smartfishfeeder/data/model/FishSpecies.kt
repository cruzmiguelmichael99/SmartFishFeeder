package com.example.smartfishfeeder.data.model

/**
 * Ideal water temperature ranges based on aquaculture research for each
 * species. These are optimal growth ranges, not just survival limits (which
 * are usually wider).
 */
enum class FishSpecies(
    val displayName: String,
    val minIdealTemp: Double,
    val maxIdealTemp: Double
) {
    TILAPIA("Tilapia", 25.0, 30.0),
    BANGUS("Bangus (Milkfish)", 25.0, 32.0),
    HIPON("Hipon (Shrimp/Prawn)", 25.0, 31.0),
    ALIMANGO("Alimango (Mud Crab)", 28.0, 30.0)
}