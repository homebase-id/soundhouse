package id.homebase.soundhouse.data

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.common.time.UnixTimeUtc
import id.homebase.api.serialization.OdinSystemSerializer
import kotlin.uuid.Uuid

internal fun trackContentJson(content: AudioTrackContent): String = OdinSystemSerializer.serialize(content)

internal fun buildTrackFile(
    content: String?,
    fileType: Int = AUDIO_TRACK_FILE_TYPE,
    payloadKeys: List<String> = listOf(AUDIO_PAYLOAD_KEY),
    fileState: String = "active",
    fileId: Uuid = Uuid.random(),
    createdMs: Long = UnixTimeUtc.now().milliseconds,
    updatedMs: Long = createdMs,
    uniqueId: Uuid = Uuid.random(),
    driveId: Uuid = audioDriveId,
    payloadIvBase64: String? = null,
): HomebaseFile {
    val contentField = if (content == null) "null" else "\"${content.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    val payloads = payloadKeys.joinToString(",") {
        """{"key": "$it", "contentType": "audio/mpeg", "bytesWritten": 128, "lastModified": $createdMs${payloadIvBase64?.let { ", \"iv\": \"$it\"" } ?: ""}}"""
    }
    val json = """{
          "fileId": "$fileId",
          "driveId": "$driveId",
          "fileState": "$fileState",
          "fileSystemType": "standard",
          "keyHeader": {
            "iv": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
            "aesKey": {"bytes": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]}
          },
          "fileMetadata": {
            "globalTransitId": "${Uuid.random()}",
            "created": $createdMs,
            "updated": $updatedMs,
            "isEncrypted": true,
            "appData": {
              "uniqueId": "$uniqueId",
              "fileType": $fileType,
              "dataType": 0,
              "userDate": $createdMs,
              "content": $contentField,
              "archivalStatus": 0
            },
            "versionTag": "${Uuid.random()}",
            "payloads": [$payloads]
          },
          "serverMetadata": {
            "accessControlList": {"requiredSecurityGroup": "owner"},
            "doNotIndex": false,
            "allowDistribution": false,
            "fileSystemType": "standard",
            "fileByteCount": 0,
            "originalRecipientCount": 0
          },
          "priority": 0,
          "fileByteCount": 0
        }"""
    return OdinSystemSerializer.deserialize<HomebaseFile>(json)
}
