// Decodes a .keelbackup exactly as iOS BackupService.decode does (JSONDecoder, .iso8601), and
// prints its record counts: proof that iOS can read a file Android wrote.
import Foundation

let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
let decoder = JSONDecoder()
decoder.dateDecodingStrategy = .iso8601
do {
    let b = try decoder.decode(KeelBackup.self, from: data)
    print("IOS_DECODE ok version=\(b.version) exportedAt=\(b.exportedAt) profiles=\(b.profiles.count) symptoms=\(b.symptoms.count) checkIns=\(b.checkIns.count) links=\(b.checkInSymptoms.count) cycle=\(b.cycleEntries.count) meds=\(b.medications.count) logs=\(b.medicationLogs.count) insights=\(b.insights.count) activity=\(b.activityLogs.count) health=\(b.healthSamples.count)")
    for m in b.medications { print("IOS_MED \(m.name) schedule=\(m.scheduleKindRaw ?? "-") times=\(m.doseTimesJSON ?? "-")") }
    for c in b.checkIns { print("IOS_CHECKIN \(c.id) \(c.date) mood=\(c.moodRaw) energy=\(c.energy) notes=\(c.notes ?? "-") alcohol=\(c.alcoholCount.map(String.init) ?? "-")") }
} catch {
    print("IOS_DECODE failed: \(error)")
    exit(1)
}
