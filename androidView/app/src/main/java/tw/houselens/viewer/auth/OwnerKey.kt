package tw.houselens.viewer.auth

// Keep the existing on-disk namespace shape; v1 uses a reserved device-local owner.
data class OwnerKey(val projectUrl: String, val userId: String)
