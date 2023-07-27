package app.almondally.network

data class RelevanceStoreRequestBodyParam(
    var long_text_variable : String?,
    var key: String?,
)
data class RelevanceStoreRequestBody(
    var params: RelevanceStoreRequestBodyParam?,
    var project: String? = "6f770fda6633-4dee-a99c-f8e72bce8f69"
)
data class RelevanceStoreResponseBodyOutput(
    var inserted: String?,
)
data class RelevanceStoreResponseBody(
    var status:String?,
    var errors:Array<String?>?,
    var output:RelevanceStoreResponseBodyOutput?
)
