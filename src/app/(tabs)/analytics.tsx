import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '../../theme/provider';
import { Card } from '../../components/ui/Card';
import { CategoryBar } from '../../components/charts/CategoryBar';
import { formatINR } from '../../utils/currency';
import * as transactions from '../../db/repositories/transactions';
import * as categories from '../../db/repositories/categories';
import type { Category, Transaction } from '../../db/types';

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

interface MerchantSummary {
  name: string;
  count: number;
  total: number;
}

interface DailyTotal {
  day: number;
  total: number;
}

interface CategorySummary {
  categoryId: string | null;
  total: number;
  category: Category | null;
}

export default function AnalyticsScreen() {
  const { colors, tokens } = useTheme();
  const now = new Date();

  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1); // 1-based
  const [loading, setLoading] = useState(true);

  const [monthlyTotal, setMonthlyTotal] = useState(0);
  const [prevTotal, setPrevTotal] = useState(0);
  const [categorySummaries, setCategorySummaries] = useState<CategorySummary[]>([]);
  const [dailyTotals, setDailyTotals] = useState<DailyTotal[]>([]);
  const [topMerchants, setTopMerchants] = useState<MerchantSummary[]>([]);
  const [currentWeekTotal, setCurrentWeekTotal] = useState(0);
  const [lastWeekTotal, setLastWeekTotal] = useState(0);

  const prevMonth = useCallback((): { year: number; month: number } => {
    if (month === 1) return { year: year - 1, month: 12 };
    return { year, month: month - 1 };
  }, [year, month]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [total, catTotals, allCategories, txs] = await Promise.all([
        transactions.getMonthlyTotal(year, month),
        transactions.getCategoryTotals(year, month),
        categories.getAll(),
        transactions.getByMonth(year, month),
      ]);

      const pm = prevMonth();
      const prev = await transactions.getMonthlyTotal(pm.year, pm.month);

      setMonthlyTotal(total);
      setPrevTotal(prev);

      // Category summaries
      const catMap = new Map<string, Category>();
      for (const c of allCategories) catMap.set(c.id, c);

      const summaries: CategorySummary[] = catTotals
        .map((ct) => ({
          categoryId: ct.categoryId,
          total: ct.total,
          category: ct.categoryId ? (catMap.get(ct.categoryId) ?? null) : null,
        }))
        .sort((a, b) => b.total - a.total);
      setCategorySummaries(summaries);

      // Daily totals
      const dayMap = new Map<number, number>();
      for (const tx of txs) {
        if (tx.status !== 'completed') continue;
        const d = new Date(tx.created_at);
        const day = d.getDate();
        dayMap.set(day, (dayMap.get(day) ?? 0) + tx.amount_paise);
      }
      const daysInMonth = new Date(year, month, 0).getDate();
      const dailies: DailyTotal[] = [];
      for (let i = 1; i <= daysInMonth; i++) {
        dailies.push({ day: i, total: dayMap.get(i) ?? 0 });
      }
      setDailyTotals(dailies);

      // Top merchants
      const merchantMap = new Map<string, { count: number; total: number }>();
      for (const tx of txs) {
        if (tx.status !== 'completed') continue;
        const name = tx.recipient_name ?? tx.recipient_upi_id ?? 'Unknown';
        const existing = merchantMap.get(name) ?? { count: 0, total: 0 };
        merchantMap.set(name, { count: existing.count + 1, total: existing.total + tx.amount_paise });
      }
      const merchants: MerchantSummary[] = Array.from(merchantMap.entries())
        .map(([name, data]) => ({ name, count: data.count, total: data.total }))
        .sort((a, b) => b.total - a.total)
        .slice(0, 5);
      setTopMerchants(merchants);

      // Week comparison
      const todayDate = new Date();
      const currentMonthYear = year === todayDate.getFullYear() && month === todayDate.getMonth() + 1;
      if (currentMonthYear) {
        const dayOfWeek = todayDate.getDay(); // 0=Sun
        const startOfCurrentWeek = new Date(todayDate);
        startOfCurrentWeek.setDate(todayDate.getDate() - dayOfWeek);
        const startOfLastWeek = new Date(startOfCurrentWeek);
        startOfLastWeek.setDate(startOfCurrentWeek.getDate() - 7);

        let cwTotal = 0;
        let lwTotal = 0;
        for (const tx of txs) {
          if (tx.status !== 'completed') continue;
          const d = new Date(tx.created_at);
          if (d >= startOfCurrentWeek) cwTotal += tx.amount_paise;
          else if (d >= startOfLastWeek) lwTotal += tx.amount_paise;
        }
        setCurrentWeekTotal(cwTotal);
        setLastWeekTotal(lwTotal);
      } else {
        setCurrentWeekTotal(0);
        setLastWeekTotal(0);
      }
    } finally {
      setLoading(false);
    }
  }, [year, month, prevMonth]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  function navigateMonth(direction: -1 | 1) {
    if (direction === -1) {
      if (month === 1) { setYear(y => y - 1); setMonth(12); }
      else setMonth(m => m - 1);
    } else {
      if (month === 12) { setYear(y => y + 1); setMonth(1); }
      else setMonth(m => m + 1);
    }
  }

  const totalForCategory = categorySummaries.reduce((s, c) => s + c.total, 0);
  const maxDaily = Math.max(...dailyTotals.map(d => d.total), 1);
  const weekMax = Math.max(currentWeekTotal, lastWeekTotal, 1);

  const pctChange = prevTotal > 0
    ? Math.round(((monthlyTotal - prevTotal) / prevTotal) * 100)
    : null;

  const pm = prevMonth();

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView contentContainerStyle={{ padding: tokens.spacing.md, gap: tokens.spacing.md }} showsVerticalScrollIndicator={false}>

        {/* Header */}
        <Text style={[styles.screenTitle, { color: colors.text, fontSize: tokens.typography.sizes.xxl }]}>
          Analytics
        </Text>

        {/* Month Selector */}
        <View style={[styles.monthSelector, { backgroundColor: colors.surface, borderColor: colors.border, borderRadius: tokens.radii.md }]}>
          <TouchableOpacity onPress={() => navigateMonth(-1)} style={styles.arrowBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Text style={[styles.arrow, { color: colors.primary }]}>‹</Text>
          </TouchableOpacity>
          <Text style={[styles.monthLabel, { color: colors.text, fontSize: tokens.typography.sizes.lg }]}>
            {MONTH_NAMES[month - 1]} {year}
          </Text>
          <TouchableOpacity onPress={() => navigateMonth(1)} style={styles.arrowBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
            <Text style={[styles.arrow, { color: colors.primary }]}>›</Text>
          </TouchableOpacity>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color={colors.primary} style={{ marginTop: tokens.spacing.xl }} />
        ) : (
          <>
            {/* Monthly Total */}
            <Card>
              <Text style={[styles.sectionLabel, { color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }]}>
                Total Spent
              </Text>
              <Text style={[styles.bigNumber, { color: colors.text, fontSize: tokens.typography.sizes.xxxl }]}>
                {formatINR(monthlyTotal)}
              </Text>
              {pctChange !== null && (
                <Text style={[
                  styles.comparison,
                  { fontSize: tokens.typography.sizes.sm, color: pctChange > 0 ? colors.error : colors.success },
                ]}>
                  {pctChange > 0 ? '↑' : '↓'} {Math.abs(pctChange)}% from {MONTH_NAMES[pm.month - 1]}
                </Text>
              )}
              {pctChange === null && prevTotal === 0 && (
                <Text style={[styles.comparison, { fontSize: tokens.typography.sizes.sm, color: colors.textSecondary }]}>
                  No data for {MONTH_NAMES[pm.month - 1]}
                </Text>
              )}
            </Card>

            {/* Category Breakdown */}
            {categorySummaries.length > 0 && (
              <Card>
                <Text style={[styles.sectionTitle, { color: colors.text, fontSize: tokens.typography.sizes.md, marginBottom: tokens.spacing.md }]}>
                  Category Breakdown
                </Text>
                <View style={{ gap: tokens.spacing.md }}>
                  {categorySummaries.map((cs, idx) => {
                    const cat = cs.category;
                    const pct = totalForCategory > 0 ? (cs.total / totalForCategory) * 100 : 0;
                    const color = cat?.color ?? colors.categoryColors[idx % colors.categoryColors.length];
                    return (
                      <CategoryBar
                        key={cs.categoryId ?? 'uncategorized'}
                        icon={cat?.icon ?? '📦'}
                        name={cat?.name ?? 'Uncategorized'}
                        amount={cs.total}
                        percentage={pct}
                        color={color}
                      />
                    );
                  })}
                </View>
              </Card>
            )}

            {/* Daily Trend */}
            <Card padding="none">
              <View style={{ padding: tokens.spacing.md, paddingBottom: tokens.spacing.sm }}>
                <Text style={[styles.sectionTitle, { color: colors.text, fontSize: tokens.typography.sizes.md }]}>
                  Daily Spend
                </Text>
              </View>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: tokens.spacing.md, paddingBottom: tokens.spacing.md, gap: 4, alignItems: 'flex-end' }}>
                {dailyTotals.map(({ day, total }) => {
                  const barH = total > 0 ? Math.max(4, Math.round((total / maxDaily) * 80)) : 2;
                  const isToday = year === now.getFullYear() && month === now.getMonth() + 1 && day === now.getDate();
                  return (
                    <View key={day} style={{ alignItems: 'center', gap: 4, width: 22 }}>
                      <View
                        style={{
                          width: 14,
                          height: barH,
                          borderRadius: 3,
                          backgroundColor: isToday ? colors.primary : (total > 0 ? colors.categoryColors[2] : colors.border),
                        }}
                      />
                      <Text style={{ fontSize: 9, color: colors.textSecondary }}>{day}</Text>
                    </View>
                  );
                })}
              </ScrollView>
            </Card>

            {/* Top Merchants */}
            {topMerchants.length > 0 && (
              <Card>
                <Text style={[styles.sectionTitle, { color: colors.text, fontSize: tokens.typography.sizes.md, marginBottom: tokens.spacing.md }]}>
                  Top Merchants
                </Text>
                <View style={{ gap: tokens.spacing.sm }}>
                  {topMerchants.map((m, idx) => (
                    <View key={m.name} style={[styles.merchantRow, { borderBottomColor: colors.border, borderBottomWidth: idx < topMerchants.length - 1 ? 1 : 0, paddingBottom: idx < topMerchants.length - 1 ? tokens.spacing.sm : 0 }]}>
                      <View style={{ flex: 1 }}>
                        <Text style={[{ color: colors.text, fontSize: tokens.typography.sizes.sm, fontWeight: '500' }]}>
                          {m.name}
                        </Text>
                        <Text style={[{ color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
                          {m.count} {m.count === 1 ? 'transaction' : 'transactions'}
                        </Text>
                      </View>
                      <Text style={[{ color: colors.text, fontWeight: '600', fontSize: tokens.typography.sizes.sm }]}>
                        {formatINR(m.total)}
                      </Text>
                    </View>
                  ))}
                </View>
              </Card>
            )}

            {/* Week Comparison */}
            <Card>
              <Text style={[styles.sectionTitle, { color: colors.text, fontSize: tokens.typography.sizes.md, marginBottom: tokens.spacing.md }]}>
                Week Comparison
              </Text>
              <View style={{ gap: tokens.spacing.sm }}>
                <View style={{ gap: 4 }}>
                  <View style={styles.weekLabelRow}>
                    <Text style={{ color: colors.text, fontSize: tokens.typography.sizes.sm, fontWeight: '500' }}>This Week</Text>
                    <Text style={{ color: colors.text, fontSize: tokens.typography.sizes.sm, fontWeight: '600' }}>{formatINR(currentWeekTotal)}</Text>
                  </View>
                  <View style={[styles.weekTrack, { backgroundColor: colors.border, borderRadius: tokens.radii.full }]}>
                    <View style={[styles.weekFill, { width: `${Math.round((currentWeekTotal / weekMax) * 100)}%` as `${number}%`, backgroundColor: colors.primary, borderRadius: tokens.radii.full }]} />
                  </View>
                </View>
                <View style={{ gap: 4 }}>
                  <View style={styles.weekLabelRow}>
                    <Text style={{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }}>Last Week</Text>
                    <Text style={{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }}>{formatINR(lastWeekTotal)}</Text>
                  </View>
                  <View style={[styles.weekTrack, { backgroundColor: colors.border, borderRadius: tokens.radii.full }]}>
                    <View style={[styles.weekFill, { width: `${Math.round((lastWeekTotal / weekMax) * 100)}%` as `${number}%`, backgroundColor: colors.textSecondary, borderRadius: tokens.radii.full }]} />
                  </View>
                </View>
              </View>
            </Card>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  screenTitle: { fontWeight: '700' },
  monthSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
    paddingVertical: 10,
    borderWidth: 1,
  },
  arrowBtn: { padding: 4 },
  arrow: { fontSize: 28, fontWeight: '300', lineHeight: 32 },
  monthLabel: { fontWeight: '600' },
  sectionLabel: { marginBottom: 4 },
  bigNumber: { fontWeight: '700', marginVertical: 4 },
  comparison: { marginTop: 2 },
  sectionTitle: { fontWeight: '600' },
  merchantRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  weekLabelRow: { flexDirection: 'row', justifyContent: 'space-between' },
  weekTrack: { height: 10, overflow: 'hidden' },
  weekFill: { height: 10 },
});
