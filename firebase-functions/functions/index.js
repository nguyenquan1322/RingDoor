const { onValueWritten } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.notifyDoorbell = onValueWritten(
  {
    ref: "/Devices/{deviceId}/statusRing",
    region: "us-central1",
  },
  async (event) => {
    const before = event.data.before.val();
    const after = event.data.after.val();

    // Chỉ gửi khi giá trị chuyển sang "ringOn"
    if (after !== "ringOn" || before === "ringOn") return;

    console.log("🔔 Doorbell pressed — sending FCM...");

    await getMessaging().send({
      topic: "ringdoor",
      data: {
        type: "doorbell",
        title: "🔔 Chuông cửa",
        body: "Có người đang bấm chuông!",
      },
      android: {
        priority: "high"
      }
    });

    console.log("📨 FCM sent!");
  }
);
