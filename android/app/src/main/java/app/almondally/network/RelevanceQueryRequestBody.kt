package app.almondally.network

data class RelevanceQueryRequestBodyParam(
    var long_text_variable : String?,
    var text_variable: String?,
    var patient_name : String?,
    var caregiver_name: String?,
    var caregiver_role : String?
)
data class RelevanceQueryRequestBody(
    var params: RelevanceQueryRequestBodyParam?,
    var project: String? = "6f770fda6633-4dee-a99c-f8e72bce8f69"
)
data class RelevanceQueryResponseBodyOutput(
    var answer:String?,
    var prompt:String?,
    var user_key_used:Boolean?,
    var validation_history:Array<String?>?,
    var credits_cost:Double?
)
data class RelevanceQueryResponseBody(
    var status:String?,
    var errors:Array<String?>?,
    var output:RelevanceQueryResponseBodyOutput?
)
