const { onDocumentUpdated, onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");
admin.initializeApp();

/**
 * 當 posts/{postId} 的 participants 陣列有新成員加入時，
 * 發送推播通知給活動發起人。
 */
exports.notifyOnJoin = onDocumentUpdated("posts/{postId}", async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();

    const beforeParticipants = before.participants || [];
    const afterParticipants = after.participants || [];

    // 確認是新增參加者（不是退出）
    if (afterParticipants.length <= beforeParticipants.length) return null;

    // 找出新加入者的 UID
    const newParticipantUid = afterParticipants.find(
        uid => !beforeParticipants.includes(uid)
    );
    if (!newParticipantUid) return null;

    const authorId = after.authorId;

    // 自己加入自己的活動，不發通知
    if (newParticipantUid === authorId) return null;

    // 取得發起人的 fcmToken
    const authorDoc = await admin.firestore()
        .collection("users").doc(authorId).get();
    const fcmToken = authorDoc.data()?.fcmToken;
    if (!fcmToken) return null;

    // 取得新加入者的名稱
    const newUserDoc = await admin.firestore()
        .collection("users").doc(newParticipantUid).get();
    const newUserName = newUserDoc.data()?.displayName || "有人";

    const postTitle = after.title || "你的活動";

    // 發送 FCM 推播
    return admin.messaging().send({
        token: fcmToken,
        notification: {
            title: `「${postTitle}」有新成員加入！`,
            body: `${newUserName} 加入了你的活動`,
        },
        data: {
            postId: event.params.postId,
        },
        android: {
            priority: "high",
            notification: {
                channelId: "grouping_notifications",
                sound: "default",
            },
        },
    });
});

/**
 * 當 kickNotifications/{docId} 被建立時，
 * 發送推播通知給被踢出的使用者，並刪除該通知文件。
 */
exports.notifyOnKick = onDocumentCreated("kickNotifications/{docId}", async (event) => {
    const data = event.data.data();
    if (!data) return null;

    const { kickedUid, postTitle, authorName } = data;

    // 取得被踢使用者的 FCM Token
    const kickedUserDoc = await admin.firestore()
        .collection("users").doc(kickedUid).get();
    const fcmToken = kickedUserDoc.data()?.fcmToken;

    // 刪除通知文件（避免重複觸發）
    await event.data.ref.delete();

    if (!fcmToken) return null;

    return admin.messaging().send({
        token: fcmToken,
        notification: {
            title: "你已被移出活動",
            body: `${authorName} 將你從「${postTitle}」中移除`,
        },
        android: {
            priority: "high",
            notification: {
                channelId: "grouping_notifications",
                sound: "default",
            },
        },
    });
});
