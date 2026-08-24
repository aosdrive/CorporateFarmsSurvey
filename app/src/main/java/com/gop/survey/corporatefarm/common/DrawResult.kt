package com.gop.survey.corporatefarm.common




sealed class DrawResult {
    /** A vertex was accepted. [pointCount] is the new total. */
    data class PointAdded(val pointCount: Int) : DrawResult()

    /** The tap fell outside the AOI boundary. */
    object OutsideBoundary : DrawResult()

    /** The tap fell inside a parcel that already exists. */
    object InsideExistingParcel : DrawResult()

    /** Draw mode cannot start because no boundary has been loaded. */
    object NoBoundary : DrawResult()

    /** Fewer than the required number of vertices. */
    data class TooFewPoints(val required: Int) : DrawResult()

    /** The map has no spatial reference yet. */
    object MapNotReady : DrawResult()

    /** Draw mode started or the polygon was built successfully. */
    object Ok : DrawResult()
}
