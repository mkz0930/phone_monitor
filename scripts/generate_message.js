import "dotenv/config";
import { DateTime } from "luxon";
import { loadReport } from "../lib/storage.js";
import { formatNoDataMessage, formatReportMessage } from "../lib/format.js";

function parseArgs() {
  const args = process.argv.slice(2);
  const out = {};
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--date") {
      out.date = args[i + 1];
      i++;
    } else if (args[i] === "--top") {
      out.top = Number(args[i + 1]);
      i++;
    }
  }
  return out;
}

const { date, top } = parseArgs();
const targetDate = date || DateTime.now().setZone("Asia/Shanghai").toISODate();
const report = loadReport(targetDate);

if (!report) {
  console.log(formatNoDataMessage(targetDate));
  process.exit(0);
}

console.log(formatReportMessage(report, { topN: Number.isFinite(top) ? top : 10 }));
