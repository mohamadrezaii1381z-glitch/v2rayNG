package com.v2ray.ang.hardvpn.domain

/** Everything the UI needs about one domain, read from storage in a single pass. */
data class DomainSnapshot(
    val servers: List<ServerUiModel>,
    val sources: List<SourceUiModel>,
    val selectedGuid: String?,
    val autoSelect: Boolean,
)

/** Aggregated result of refreshing the sources of one domain. */
data class RefreshOutcome(
    val successCount: Int,
    val failureCount: Int,
    val configCount: Int,
) {
    val hasFailure: Boolean get() = failureCount > 0
    val hasSuccess: Boolean get() = successCount > 0
}

sealed interface AddSourceResult {
    data class Success(val configCount: Int) : AddSourceResult
    data object Empty : AddSourceResult
    data object InvalidUrl : AddSourceResult
    data object Failed : AddSourceResult
}

/** Result of a manual import attempt. */
data class ManualImportResult(
    val count: Int,
    val sourceUrlRejected: Boolean,
    val subscriptionSourcesIgnored: Int = 0,
)
