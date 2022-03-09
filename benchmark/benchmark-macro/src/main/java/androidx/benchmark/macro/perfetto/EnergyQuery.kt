/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.benchmark.macro.perfetto

// We want to use android_powrails.sql, but cannot as they do not split into sections with slice

internal object EnergyQuery {
    private fun getFullQuery(slice: Slice) = """
        SELECT
            t.name,
            max(c.value) - min(c.value) AS energyUs
        FROM counter c
        JOIN counter_track t ON c.track_id = t.id
        WHERE t.name GLOB 'power.*'
        AND c.ts >= ${slice.ts} AND c.ts <= ${slice.endTs}
        GROUP BY t.name
    """.trimIndent()

    data class EnergyMetrics(
        val name: String,
        val energyUs: Double
    )

    fun getEnergyMetrics(
        absoluteTracePath: String,
        slice: Slice
    ): List<EnergyMetrics> {
        val queryResult = PerfettoTraceProcessor.rawQuery(
            absoluteTracePath = absoluteTracePath,
            query = getFullQuery(slice)
        )

        val resultLines = queryResult.split("\n")

        if (resultLines.first() != """"name","energyUs"""") {
            throw IllegalStateException("query failed!\n${getFullQuery(slice)}")
        }

        // results are in CSV with a header row, and strings wrapped with quotes
        return resultLines
            .filter { it.isNotBlank() } // drop blank lines
            .drop(1) // drop the header row
            .map {
                val columns = it.split(",")
                EnergyMetrics(
                    name = columns[0].unquote().camelCase(),
                    energyUs = columns[1].toDouble(),
                )
            }
    }
}