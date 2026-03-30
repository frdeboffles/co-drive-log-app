package com.codrivelog.core.dr2324

object Dr2324Mapper {
    const val ROWS_PER_PAGE = 13

    fun map(
        studentProfile: StudentProfile,
        sessions: List<DriveSession>,
    ): Dr2324Document {
        val rows = sessions
            .sortedBy { it.date }
            .map { session ->
                Dr2324Row(
                    date = Dr2324Formatters.formatDate(session.date),
                    verifierInitials = session.verifierInitials,
                    drivingTime = Dr2324Formatters.formatTime(session.totalMinutes),
                    nightDrivingTime = Dr2324Formatters.formatTime(session.nightMinutes),
                    comments = session.comments,
                    totalMinutes = session.totalMinutes,
                    nightMinutes = session.nightMinutes,
                )
            }

        val pages = if (rows.isEmpty()) {
            listOf(
                Dr2324Page(
                    rows = emptyList(),
                    pageTotalMinutes = 0,
                    pageNightMinutes = 0,
                )
            )
        } else {
            rows.chunked(ROWS_PER_PAGE).map { chunk ->
                Dr2324Page(
                    rows = chunk,
                    pageTotalMinutes = chunk.sumOf { it.totalMinutes },
                    pageNightMinutes = chunk.sumOf { it.nightMinutes },
                )
            }
        }

        return Dr2324Document(
            studentProfile = studentProfile,
            pages = pages,
            grandTotalMinutes = rows.sumOf { it.totalMinutes },
            grandNightMinutes = rows.sumOf { it.nightMinutes },
        )
    }
}
