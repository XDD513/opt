/**
 * 随机数据与唯一值生成器（健康档案、注册字段）。
 * 各 100 条固定池已整体换新文案，便于批量造数时数据分布多样。
 */
const BLOOD_TYPES = ["A", "B", "AB", "O"];

function buildPoolFromParts(partA, partB, partC, size = 100) {
  const out = [];
  for (const a of partA) {
    for (const b of partB) {
      for (const c of partC) {
        out.push(`${a}${b}${c}`);
        if (out.length >= size) return out;
      }
    }
  }
  return out.slice(0, size);
}

function buildFixed100Pool(poolName, partA, partB, partC) {
  const pool = buildPoolFromParts(partA, partB, partC, 100);
  if (pool.length !== 100) {
    throw new Error(`${poolName} must contain exactly 100 entries`);
  }
  return pool;
}

const ALLERGY_POOL = buildFixed100Pool(
  "ALLERGY_POOL",
  ["柳絮", "动物皮屑", "冷空气", "紫外线", "豆制品", "小麦", "乳制品", "甲壳类", "碘剂", "阿司匹林"],
  ["急性", "慢性", "间歇", "接触诱发", "进食后", "运动后", "夜间加重", "晨起明显"],
  ["；已记录过敏原并回避", "；偶发风团可自行缓解", "；需随身携带抗过敏药", "；建议免疫科随访", "；症状轻未长期用药"]
);

const MEDICAL_POOL = buildFixed100Pool(
  "MEDICAL_POOL",
  ["既往史", "社区筛查", "年度体检", "门诊复查", "慢病管理档案"],
  ["甲状腺结节", "胆囊息肉", "过敏性鼻炎", "睡眠呼吸暂停", "骨质疏松倾向"],
  ["；随访影像稳定", "；生活方式干预中", "；药物控制良好", "；暂无需手术", "；建议半年复查"]
);

const FAMILY_POOL = buildFixed100Pool(
  "FAMILY_POOL",
  ["直系亲属", "旁系亲属", "祖辈", "同辈", "子代"],
  ["肿瘤病史", "自身免疫病", "代谢综合征", "早发心血管事件", "精神心理疾患"],
  ["；本人筛查未见异常", "；已做遗传咨询", "；建议年度专项体检", "；保持随访意识", "；生活方式已调整"]
);

const LIFESTYLE_POOL = buildFixed100Pool(
  "LIFESTYLE_POOL",
  ["夜班轮班", "久坐办公", "高频出差", "居家办公", "备考熬夜", "规律晨练", "控盐控油", "地中海饮食尝试", "间歇性断食", "素食为主"],
  ["；日均饮水≥1.5L", "；咖啡因摄入控制", "；屏幕时间已管理", "；正念冥想练习", "；步数目标8000+"],
  ["；主观精力尚可", "；近期在改善睡眠", "；压力源已识别", "；社交支持良好", "；继续观察即可"]
);

function randInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function pick(arr) {
  return arr[randInt(0, arr.length - 1)];
}

function pad2(n) {
  return String(n).padStart(2, "0");
}

function randomBirthDate() {
  const year = randInt(1988, 2005);
  const month = randInt(1, 12);
  const dayMax = new Date(year, month, 0).getDate();
  const day = randInt(1, dayMax);
  return `${year}-${pad2(month)}-${pad2(day)}`;
}

function ageFromBirthDate(birthDate) {
  const year = Number(String(birthDate).slice(0, 4));
  const nowYear = new Date().getFullYear();
  return Math.max(1, nowYear - year);
}

function idCardChecksum(base17) {
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const map = ["1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"];
  const sum = base17.split("").reduce((acc, c, idx) => acc + Number(c) * weights[idx], 0);
  return map[sum % 11];
}

function generateIdCard(birthDate, genderCode, usedIdCards) {
  const birth = birthDate.replace(/-/g, "");
  while (true) {
    const area = String(randInt(110101, 659004));
    const seqBase = randInt(10, 99) * 10;
    const parity = genderCode === 1 ? 1 : 0;
    const seq = String(seqBase + parity).padStart(3, "0");
    const base17 = `${area}${birth}${seq}`;
    const full = `${base17}${idCardChecksum(base17)}`;
    if (!usedIdCards.has(full)) {
      usedIdCards.add(full);
      return full;
    }
  }
}

function generatePhone(usedPhones) {
  const prefixes = ["13", "14", "15", "16", "17", "18", "19"];
  while (true) {
    const p = pick(prefixes) + String(randInt(100000000, 999999999));
    if (!usedPhones.has(p)) {
      usedPhones.add(p);
      return p;
    }
  }
}

/**
 * 用户名：前缀 + 序号，序号宽度随批量上限自动扩展（支持 1000+ 不撞号、不超 20 字符）。
 */
function generateUsername(index, usedUsernames, usernamePrefix, startSeq, totalCount) {
  const seqNum = startSeq + index - 1;
  const digitWidth = Math.max(3, String(startSeq + totalCount - 1).length);
  const seq = String(seqNum).padStart(digitWidth, "0");
  const name = `${usernamePrefix}${seq}`;
  if (name.length < 3 || name.length > 20) {
    throw new Error(`username "${name}" length must be 3-20 (缩短 usernamePrefix 或减小批量)`);
  }
  if (usedUsernames.has(name)) {
    throw new Error(`duplicate username generated: ${name}`);
  }
  usedUsernames.add(name);
  return name;
}

function generateRealName(index, usedRealNames) {
  const family = ["王", "李", "张", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "马", "朱", "胡"];
  const given1 = ["沐", "若", "景", "清", "知", "以", "向", "予", "言", "初"];
  const given2 = ["川", "岚", "禾", "洲", "澄", "屿", "溪", "棠", "珩", "砚"];
  /** 后端校验：仅限汉字及中间点，禁止数字字母（见 RegisterRequest.realName） */
  const tail = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥春夏秋冬东南西北";
  while (true) {
    const a = index % tail.length;
    const b = Math.floor(index / tail.length) % tail.length;
    const n = `${pick(family)}${pick(given1)}${pick(given2)}${tail[a]}${tail[b]}`;
    if (!usedRealNames.has(n)) {
      usedRealNames.add(n);
      return n;
    }
  }
}

function createHealthProfile(userId, realName, gender, birthDate) {
  const age = ageFromBirthDate(birthDate);
  const height = randInt(150, 190);
  const weight = randInt(45, 95);
  return {
    userId,
    userName: realName,
    gender,
    age,
    bloodType: pick(BLOOD_TYPES),
    height,
    weight,
    allergyHistory: pick(ALLERGY_POOL),
    medicalHistory: pick(MEDICAL_POOL),
    familyHistory: pick(FAMILY_POOL),
    lifestyle: pick(LIFESTYLE_POOL),
  };
}

function createUserSeeds(count, options = {}) {
  const usernamePrefix = String(options.usernamePrefix || "").trim();
  const startSeq = Number(options.startSeq || 1);
  if (!usernamePrefix) {
    throw new Error("usernamePrefix is required");
  }
  if (!Number.isInteger(startSeq) || startSeq < 1) {
    throw new Error("startSeq must be a positive integer");
  }
  if (!Number.isInteger(count) || count < 1) {
    throw new Error("count must be a positive integer");
  }

  const usedUsernames = new Set();
  const usedPhones = new Set();
  const usedIdCards = new Set();
  const usedRealNames = new Set();
  const users = [];

  for (let i = 1; i <= count; i += 1) {
    const gender = Math.random() < 0.5 ? 1 : 2;
    const birthDate = randomBirthDate();
    const username = generateUsername(i, usedUsernames, usernamePrefix, startSeq, count);
    const realName = generateRealName(i, usedRealNames);
    const phone = generatePhone(usedPhones);
    const idCard = generateIdCard(birthDate, gender, usedIdCards);
    users.push({
      username,
      password: "123456",
      confirmPassword: "123456",
      realName,
      phone,
      idCard,
      gender,
      birthDate,
    });
  }

  return users;
}

module.exports = {
  createUserSeeds,
  createHealthProfile,
};
