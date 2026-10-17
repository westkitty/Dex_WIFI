package com.example.data

import com.example.model.ApartmentLayout
import com.example.model.NormalizedPoint
import com.example.model.RoomType
import com.example.model.RoomZone
import com.example.model.SurveyPoint

object DefaultApartments {

    val twoBedroomApartment = ApartmentLayout(
        id = "apt_2bed",
        name = "2-Bedroom Apartment",
        description = "Standard 850 sq ft apartment with living area, 2 bedrooms, kitchen and bath.",
        widthMeters = 12f,
        heightMeters = 9f,
        rooms = listOf(
            RoomZone("living", "Living Room", 0.05f, 0.05f, 0.50f, 0.48f, RoomType.LIVING_ROOM),
            RoomZone("kitchen", "Kitchen", 0.58f, 0.05f, 0.37f, 0.38f, RoomType.KITCHEN),
            RoomZone("hallway", "Hallway", 0.48f, 0.45f, 0.15f, 0.50f, RoomType.HALLWAY),
            RoomZone("master_bed", "Master Bedroom", 0.05f, 0.55f, 0.40f, 0.40f, RoomType.BEDROOM),
            RoomZone("bed_2", "Office / Bed 2", 0.65f, 0.45f, 0.30f, 0.50f, RoomType.OFFICE),
            RoomZone("bath", "Bathroom", 0.48f, 0.68f, 0.15f, 0.27f, RoomType.BATHROOM)
        ),
        defaultRouterPosition = NormalizedPoint(0.28f, 0.25f) // Living room TV console
    )

    val oneBedroomApartment = ApartmentLayout(
        id = "apt_1bed",
        name = "1-Bedroom Apartment",
        description = "Compact 600 sq ft urban apartment layout.",
        widthMeters = 10f,
        heightMeters = 8f,
        rooms = listOf(
            RoomZone("living_kitchen", "Open Living & Dining", 0.05f, 0.05f, 0.55f, 0.55f, RoomType.LIVING_ROOM),
            RoomZone("kitchen_area", "Kitchenette", 0.63f, 0.05f, 0.32f, 0.42f, RoomType.KITCHEN),
            RoomZone("bedroom", "Bedroom", 0.05f, 0.62f, 0.55f, 0.33f, RoomType.BEDROOM),
            RoomZone("bathroom", "Bathroom", 0.63f, 0.50f, 0.32f, 0.45f, RoomType.BATHROOM)
        ),
        defaultRouterPosition = NormalizedPoint(0.30f, 0.30f)
    )

    val studioLoft = ApartmentLayout(
        id = "apt_studio",
        name = "Studio Loft",
        description = "Open concept 450 sq ft studio with minimal partition walls.",
        widthMeters = 9f,
        heightMeters = 7f,
        rooms = listOf(
            RoomZone("main_studio", "Studio Main Space", 0.05f, 0.05f, 0.60f, 0.88f, RoomType.LIVING_ROOM),
            RoomZone("kitchenette", "Kitchen Area", 0.68f, 0.05f, 0.27f, 0.45f, RoomType.KITCHEN),
            RoomZone("bath_studio", "Bathroom", 0.68f, 0.53f, 0.27f, 0.40f, RoomType.BATHROOM)
        ),
        defaultRouterPosition = NormalizedPoint(0.35f, 0.40f)
    )

    val allLayouts = listOf(twoBedroomApartment, oneBedroomApartment, studioLoft)

    fun createSampleSurveyPoints(layoutId: String, routerSsid: String): List<SurveyPoint> {
        val now = System.currentTimeMillis()
        return when (layoutId) {
            "apt_2bed" -> listOf(
                // Close to router in living room (Strong)
                SurveyPoint(0, layoutId, NormalizedPoint(0.28f, 0.25f), "Living Room", routerSsid, -38, 5240, 48, now - 500000, "Right beside router"),
                SurveyPoint(0, layoutId, NormalizedPoint(0.12f, 0.15f), "Living Room", routerSsid, -46, 5240, 48, now - 400000, "Near couch"),
                // Kitchen (Through 1 wall)
                SurveyPoint(0, layoutId, NormalizedPoint(0.72f, 0.20f), "Kitchen", routerSsid, -62, 5240, 48, now - 300000, "Near dining counter"),
                // Hallway
                SurveyPoint(0, layoutId, NormalizedPoint(0.53f, 0.55f), "Hallway", routerSsid, -58, 5240, 48, now - 200000, "Central corridor"),
                // Office (Through multiple drywall walls)
                SurveyPoint(0, layoutId, NormalizedPoint(0.80f, 0.70f), "Office / Bed 2", routerSsid, -72, 2437, 6, now - 150000, "Desk corner (Fell back to 2.4GHz)"),
                // Master Bedroom corner (Far end, dead zone!)
                SurveyPoint(0, layoutId, NormalizedPoint(0.12f, 0.82f), "Master Bedroom", routerSsid, -82, 2437, 6, now - 100000, "Bedside nightstand - buffering!"),
                // Bathroom
                SurveyPoint(0, layoutId, NormalizedPoint(0.54f, 0.80f), "Bathroom", routerSsid, -76, 2437, 6, now - 50000, "Bathroom tile dampening")
            )
            "apt_1bed" -> listOf(
                SurveyPoint(0, layoutId, NormalizedPoint(0.30f, 0.30f), "Open Living & Dining", routerSsid, -40, 5240, 48, now, "Beside TV"),
                SurveyPoint(0, layoutId, NormalizedPoint(0.75f, 0.20f), "Kitchenette", routerSsid, -56, 5240, 48, now, "Countertop"),
                SurveyPoint(0, layoutId, NormalizedPoint(0.20f, 0.78f), "Bedroom", routerSsid, -74, 5240, 48, now, "Bedroom window"),
                SurveyPoint(0, layoutId, NormalizedPoint(0.78f, 0.72f), "Bathroom", routerSsid, -80, 2437, 6, now, "Far corner")
            )
            else -> listOf(
                SurveyPoint(0, layoutId, NormalizedPoint(0.35f, 0.40f), "Studio Main Space", routerSsid, -42, 5240, 48, now, "Desk"),
                SurveyPoint(0, layoutId, NormalizedPoint(0.15f, 0.15f), "Studio Main Space", routerSsid, -52, 5240, 48, now, "Window"),
                SurveyPoint(0, layoutId, NormalizedPoint(0.80f, 0.25f), "Kitchen Area", routerSsid, -60, 5240, 48, now, "Sink")
            )
        }
    }
}
