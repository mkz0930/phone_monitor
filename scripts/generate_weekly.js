import "dotenv/config";
import { DateTime } from "luxon";
import { formatWeeklyReportMessage } from "../lib/weekly.js";

function parseArgs() {
  const args = process.argv.slice(2);
  const out = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--end-date") {
      out.endDate = args[i + 1];
      i++;
    }
  }
  return out;
}

const { endDate } = parseArgs();
const targetEndDate = endDate || DateTime.now().setZone("Asia/Shanghai").toISODate();

console.log(formatWeeklyReportMessage(targetEndDate));
