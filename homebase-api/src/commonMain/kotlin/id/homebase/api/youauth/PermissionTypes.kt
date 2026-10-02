package id.homebase.api.youauth

/**
 * Permission types for app access within circles. These define what an app can do within user's
 * circles.
 */
enum class AppCirclePermissionType(val value: Int) {
    None(0),
    ReadConnections(10),
    ReadCircleMembers(50),
    ReadWhoIFollow(80),
    ReadMyFollowers(130);

    companion object {
    }
}

/** Permission types for app-level access. These define general permissions an app can request. */
enum class AppPermissionType(val value: Int) {
    None(0),
    ReadConnections(10),
    ReadConnectionRequests(30),
    ReadCircleMembers(50),
    ReadWhoIFollow(80),
    ReadMyFollowers(130),
    ManageFeed(150),
    ManageProfile(170),
    SendDataToOtherIdentitiesOnMyBehalf(210),
    ReceiveDataFromOtherIdentitiesOnMyBehalf(305),
    SendPushNotifications(405),
    PublishStaticContent(505),
    SendIntroductions(909);

    companion object {
    }
}
