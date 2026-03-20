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
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../theme/provider';
import { Card } from '../../components/ui/Card';
import { CategoryBar } from '../../components/charts/CategoryBar';
import { formatINR } from '../../utils/currency';
import * as transactions from '../../db/repositories/transactions';
import * as categories from '../../db/repositories/categories';
import type { Category } from '../../db/types';

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
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [loading, setLoading] = useState(true);

  const [monthlyTotal, setMonthlyTotal] = useState(0);
  const [prevTotal, setPrevTotal] = useState(0);
  const [categorySummaries, setCategorySummaries] = useState<CategorySummary[]>([]);
  const [dailyTotals, setDailyTotals] = useState<DailyTotal[]>([]);
  const [topMerchants, setTopMerchants] = useState<MerchantSummary[]>([]);
  const [currentWeekTotal, setCurrentWeekTotal] = useState(0);
  const [lastWeekTotal, setLastWeekTotal] = useState(0);
  const [merchantCount, setMerchantCount] = useState(0);
  const [transactionCount, setTransactionCount] = useState(0);

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

      const completedTxs = txs.filter((tx) => tx.status === 'completed');
      setTransactionCount(completedTxs.length);

      const dayMap = new Map<number, number>();
      for (const tx of completedTxs) {
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

      const merchantMap = new Map<string, { count: number; total: number }>();
      for (const tx of completedTxs) {
        const name = tx.recipient_name ?? tx.recipient_upi_id ?? 'Unknown';
        const existing = merchantMap.get(name) ?? { count: 0, total: 0 };
        merchantMap.set(name, { count: existing.count + 1, total: existing.total + tx.amount_paise });
      }
      setMerchantCount(merchantMap.size);

      const merchants: MerchantSummary[] = Array.from(merchantMap.entries())
        .map(([name, data]) => ({ name, count: data.count, total: data.total }))
        .sort((a, b) => b.total - a.total)
        .slice(0, 5);
      setTopMerchants(merchants);

      const todayDate = new Date();
      const currentMonthYear = year === todayDate.getFullYear() && month === todayDate.getMonth() + 1;
      if (currentMonthYear) {
        const dayOfWeek = todayDate.getDay();
        const startOfCurrentWeek = new Date(todayDate);
        startOfCurrentWeek.setDate(todayDate.getDate() - dayOfWeek);
        const startOfLastWeek = new Date(startOfCurrentWeek);
        startOfLastWeek.setDate(startOfCurrentWeek.getDate() - 7);

        let cwTotal = 0;
        let lwTotal = 0;
        for (const tx of completedTxs) {
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
      if (month === 1) {
        setYear((value) => value - 1);
        setMonth(12);
      } else {
        setMonth((value) => value - 1);
      }
    } else {
      if (month === 12) {
        setYear((value) => value + 1);
        setMonth(1);
      } else {
        setMonth((value) => value + 1);
      }
    }
  }

  const totalForCategory = categorySummaries.reduce((sum, category) => sum + category.total, 0);
  const maxDaily = Math.max(...dailyTotals.map((daily) => daily.total), 1);
  const weekMax = Math.max(currentWeekTotal, lastWeekTotal, 1);
  const daysWithSpend = dailyTotals.filter((daily) => daily.total > 0).length;
  const pctChange = prevTotal > 0
    ? Math.round(((monthlyTotal - prevTotal) / prevTotal) * 100)
    : null;
  const pm = prevMonth();
  const topMerchant = topMerchants[0];

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={[styles.orb, { backgroundColor: colors.primary + '10', top: -80, right: -60 }]} />
      <View style={[styles.orb, { backgroundColor: colors.warning + '0E', width: 160, height: 160, top: 180, left: -50 }]} />
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: tokens.spacing.lg,
          paddingTop: tokens.spacing.lg,
          paddingBottom: tokens.spacing.xxl + 88,
          gap: tokens.spacing.lg,
        }}
        showsVerticalScrollIndicator={false}
      >
        <View>
          <View
            style={[
              styles.eyebrowBadge,
              {
                backgroundColor: colors.surface,
                borderColor: colors.border,
                borderRadius: tokens.radii.full,
                paddingHorizontal: tokens.spacing.md,
                paddingVertical: tokens.spacing.xs,
              },
            ]}
          >
            <Text
              style={{
                color: colors.primary,
                fontSize: tokens.typography.sizes.xs,
                fontFamily: tokens.typography.families.accent,
                letterSpacing: 1,
                textTransform: 'uppercase',
              }}
            >
              Analytics
            </Text>
          </View>
          <Text
            style={{
              color: colors.text,
              fontSize: tokens.typography.sizes.xxxl,
              fontFamily: tokens.typography.families.display,
              marginTop: tokens.spacing.md,
            }}
          >
            See the month clearly.
          </Text>
          <Text
            style={{
              color: colors.textSecondary,
              fontSize: tokens.typography.sizes.md,
              lineHeight: 24,
              marginTop: tokens.spacing.sm,
              maxWidth: 340,
            }}
          >
            A lighter view of where money moved, what changed, and which categories are shaping the month.
          </Text>
        </View>

        <View
          style={[
            styles.monthSelector,
            {
              backgroundColor: colors.surface,
              borderColor: colors.border,
              borderRadius: tokens.radii.full,
              paddingHorizontal: tokens.spacing.md,
              paddingVertical: tokens.spacing.sm,
            },
          ]}
        >
          <TouchableOpacity onPress={() => navigateMonth(-1)} style={styles.arrowBtn} hitSlop={8}>
            <Ionicons name="chevron-back" size={20} color={colors.primary} />
          </TouchableOpacity>
          <Text
            style={{
              color: colors.text,
              fontSize: tokens.typography.sizes.md,
              fontFamily: tokens.typography.families.accent,
            }}
          >
            {MONTH_NAMES[month - 1]} {year}
          </Text>
          <TouchableOpacity onPress={() => navigateMonth(1)} style={styles.arrowBtn} hitSlop={8}>
            <Ionicons name="chevron-forward" size={20} color={colors.primary} />
          </TouchableOpacity>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color={colors.primary} style={{ marginTop: tokens.spacing.xl }} />
        ) : (
          <>
            <Card
              style={{
                backgroundColor: colors.text,
                borderColor: colors.text,
                padding: tokens.spacing.lg,
              }}
              padding="none"
            >
              <Text
                style={{
                  color: colors.primaryText,
                  opacity: 0.72,
                  fontSize: tokens.typography.sizes.sm,
                  fontFamily: tokens.typography.families.accent,
                }}
              >
                Total spent
              </Text>
              <Text
                style={{
                  color: colors.primaryText,
                  fontSize: tokens.typography.sizes.xxxl,
                  fontFamily: tokens.typography.families.display,
                  marginTop: tokens.spacing.sm,
                }}
              >
                {formatINR(monthlyTotal)}
              </Text>
              <Text
                style={{
                  color: pctChange === null
                    ? colors.primaryText
                    : pctChange > 0
                      ? '#F7B29A'
                      : '#98E0C4',
                  fontSize: tokens.typography.sizes.sm,
                  marginTop: tokens.spacing.sm,
                  opacity: pctChange === null ? 0.72 : 1,
                }}
              >
                {pctChange !== null
                  ? `${pctChange > 0 ? 'Up' : 'Down'} ${Math.abs(pctChange)}% from ${MONTH_NAMES[pm.month - 1]}`
                  : `No comparison data for ${MONTH_NAMES[pm.month - 1]}`}
              </Text>

              <View style={[styles.heroStatsRow, { marginTop: tokens.spacing.lg }]}>
                <View style={[styles.heroStatTile, { backgroundColor: 'rgba(255,255,255,0.08)', borderRadius: tokens.radii.md }]}>
                  <Text style={[styles.heroStatLabel, { color: colors.primaryText }]}>Transactions</Text>
                  <Text style={[styles.heroStatValue, { color: colors.primaryText, fontFamily: tokens.typography.families.accent }]}>
                    {transactionCount}
                  </Text>
                </View>
                <View style={[styles.heroStatTile, { backgroundColor: 'rgba(255,255,255,0.08)', borderRadius: tokens.radii.md }]}>
                  <Text style={[styles.heroStatLabel, { color: colors.primaryText }]}>Days active</Text>
                  <Text style={[styles.heroStatValue, { color: colors.primaryText, fontFamily: tokens.typography.families.accent }]}>
                    {daysWithSpend}
                  </Text>
                </View>
              </View>
            </Card>

            <View style={[styles.secondaryStatsRow, { gap: tokens.spacing.sm }]}>
              <Card style={styles.secondaryStatCard}>
                <Text style={[styles.secondaryStatLabel, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
                  Categories
                </Text>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xxl,
                    fontFamily: tokens.typography.families.display,
                    marginTop: tokens.spacing.sm,
                  }}
                >
                  {categorySummaries.length}
                </Text>
              </Card>
              <Card style={styles.secondaryStatCard}>
                <Text style={[styles.secondaryStatLabel, { color: colors.textSecondary, fontSize: tokens.typography.sizes.xs }]}>
                  Merchants
                </Text>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xxl,
                    fontFamily: tokens.typography.families.display,
                    marginTop: tokens.spacing.sm,
                  }}
                >
                  {merchantCount}
                </Text>
              </Card>
            </View>

            {categorySummaries.length > 0 ? (
              <Card>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xl,
                    fontFamily: tokens.typography.families.display,
                    marginBottom: tokens.spacing.md,
                  }}
                >
                  Category breakdown
                </Text>
                <View style={{ gap: tokens.spacing.md }}>
                  {categorySummaries.map((summary, index) => {
                    const category = summary.category;
                    const percentage = totalForCategory > 0 ? (summary.total / totalForCategory) * 100 : 0;
                    const color = category?.color ?? colors.categoryColors[index % colors.categoryColors.length];
                    return (
                      <CategoryBar
                        key={summary.categoryId ?? 'uncategorized'}
                        icon={category?.icon ?? '📦'}
                        name={category?.name ?? 'Uncategorized'}
                        amount={summary.total}
                        percentage={percentage}
                        color={color}
                      />
                    );
                  })}
                </View>
              </Card>
            ) : (
              <Card style={{ alignItems: 'center', paddingVertical: tokens.spacing.xl }}>
                <View
                  style={[
                    styles.emptyIcon,
                    {
                      backgroundColor: colors.primary + '12',
                      borderRadius: tokens.radii.full,
                    },
                  ]}
                >
                  <Ionicons name="pie-chart-outline" size={24} color={colors.primary} />
                </View>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xl,
                    fontFamily: tokens.typography.families.display,
                    marginTop: tokens.spacing.md,
                  }}
                >
                  No completed data yet.
                </Text>
                <Text
                  style={{
                    color: colors.textSecondary,
                    fontSize: tokens.typography.sizes.sm,
                    lineHeight: 22,
                    textAlign: 'center',
                    marginTop: tokens.spacing.sm,
                    maxWidth: 260,
                  }}
                >
                  Once payments are marked completed, this screen will start showing trends and category insights.
                </Text>
              </Card>
            )}

            <Card padding="none">
              <View style={{ padding: tokens.spacing.md, paddingBottom: tokens.spacing.sm }}>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xl,
                    fontFamily: tokens.typography.families.display,
                  }}
                >
                  Daily spend
                </Text>
                <Text
                  style={{
                    color: colors.textSecondary,
                    fontSize: tokens.typography.sizes.sm,
                    marginTop: 4,
                  }}
                >
                  The rhythm of the month, one day at a time.
                </Text>
              </View>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{
                  paddingHorizontal: tokens.spacing.md,
                  paddingBottom: tokens.spacing.md,
                  gap: 6,
                  alignItems: 'flex-end',
                }}
              >
                {dailyTotals.map(({ day, total }) => {
                  const barHeight = total > 0 ? Math.max(8, Math.round((total / maxDaily) * 100)) : 4;
                  const isToday = year === now.getFullYear() && month === now.getMonth() + 1 && day === now.getDate();
                  return (
                    <View key={day} style={styles.dailyBarWrap}>
                      <View
                        style={[
                          styles.dailyBar,
                          {
                            height: barHeight,
                            borderRadius: tokens.radii.full,
                            backgroundColor: isToday
                              ? colors.primary
                              : total > 0
                                ? colors.categoryColors[2]
                                : colors.border,
                          },
                        ]}
                      />
                      <Text style={{ fontSize: 10, color: colors.textSecondary }}>{day}</Text>
                    </View>
                  );
                })}
              </ScrollView>
            </Card>

            {topMerchants.length > 0 ? (
              <Card>
                <Text
                  style={{
                    color: colors.text,
                    fontSize: tokens.typography.sizes.xl,
                    fontFamily: tokens.typography.families.display,
                    marginBottom: tokens.spacing.md,
                  }}
                >
                  Top merchants
                </Text>
                <View style={{ gap: tokens.spacing.sm }}>
                  {topMerchants.map((merchant, index) => (
                    <View
                      key={merchant.name}
                      style={[
                        styles.merchantRow,
                        {
                          backgroundColor: index === 0 ? colors.background : 'transparent',
                          borderColor: colors.border,
                          borderRadius: tokens.radii.md,
                          padding: index === 0 ? tokens.spacing.md : 0,
                          borderWidth: index === 0 ? 1 : 0,
                        },
                      ]}
                    >
                      <View style={styles.merchantLeft}>
                        <View
                          style={[
                            styles.rankBadge,
                            {
                              backgroundColor: index === 0 ? colors.primary + '16' : colors.surface,
                              borderRadius: tokens.radii.full,
                            },
                          ]}
                        >
                          <Text
                            style={{
                              color: index === 0 ? colors.primary : colors.textSecondary,
                              fontSize: tokens.typography.sizes.xs,
                              fontFamily: tokens.typography.families.accent,
                            }}
                          >
                            {index + 1}
                          </Text>
                        </View>
                        <View style={{ flex: 1 }}>
                          <Text
                            style={{
                              color: colors.text,
                              fontSize: tokens.typography.sizes.sm,
                              fontFamily: tokens.typography.families.accent,
                            }}
                          >
                            {merchant.name}
                          </Text>
                          <Text
                            style={{
                              color: colors.textSecondary,
                              fontSize: tokens.typography.sizes.xs,
                              marginTop: 2,
                            }}
                          >
                            {merchant.count} {merchant.count === 1 ? 'transaction' : 'transactions'}
                          </Text>
                        </View>
                      </View>
                      <Text
                        style={{
                          color: colors.text,
                          fontSize: tokens.typography.sizes.sm,
                          fontFamily: tokens.typography.families.accent,
                        }}
                      >
                        {formatINR(merchant.total)}
                      </Text>
                    </View>
                  ))}
                </View>
              </Card>
            ) : null}

            <Card>
              <View style={styles.weekHeader}>
                <View>
                  <Text
                    style={{
                      color: colors.text,
                      fontSize: tokens.typography.sizes.xl,
                      fontFamily: tokens.typography.families.display,
                    }}
                  >
                    Weekly comparison
                  </Text>
                  <Text
                    style={{
                      color: colors.textSecondary,
                      fontSize: tokens.typography.sizes.sm,
                      marginTop: 4,
                    }}
                  >
                    A quick sense of momentum for the current month.
                  </Text>
                </View>
                {topMerchant ? (
                  <View
                    style={[
                      styles.weekCallout,
                      {
                        backgroundColor: colors.background,
                        borderColor: colors.border,
                        borderRadius: tokens.radii.full,
                      },
                    ]}
                  >
                    <Ionicons name="sparkles-outline" size={14} color={colors.primary} />
                    <Text
                      style={{
                        color: colors.text,
                        fontSize: tokens.typography.sizes.xs,
                        fontFamily: tokens.typography.families.accent,
                      }}
                    >
                      {topMerchant.name}
                    </Text>
                  </View>
                ) : null}
              </View>
              <View style={{ gap: tokens.spacing.md, marginTop: tokens.spacing.md }}>
                <View style={{ gap: 6 }}>
                  <View style={styles.weekLabelRow}>
                    <Text
                      style={{
                        color: colors.text,
                        fontSize: tokens.typography.sizes.sm,
                        fontFamily: tokens.typography.families.accent,
                      }}
                    >
                      This week
                    </Text>
                    <Text
                      style={{
                        color: colors.text,
                        fontSize: tokens.typography.sizes.sm,
                        fontFamily: tokens.typography.families.accent,
                      }}
                    >
                      {formatINR(currentWeekTotal)}
                    </Text>
                  </View>
                  <View style={[styles.weekTrack, { backgroundColor: colors.border, borderRadius: tokens.radii.full }]}>
                    <View
                      style={[
                        styles.weekFill,
                        {
                          width: `${Math.round((currentWeekTotal / weekMax) * 100)}%` as `${number}%`,
                          backgroundColor: colors.primary,
                          borderRadius: tokens.radii.full,
                        },
                      ]}
                    />
                  </View>
                </View>
                <View style={{ gap: 6 }}>
                  <View style={styles.weekLabelRow}>
                    <Text style={{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }}>
                      Last week
                    </Text>
                    <Text style={{ color: colors.textSecondary, fontSize: tokens.typography.sizes.sm }}>
                      {formatINR(lastWeekTotal)}
                    </Text>
                  </View>
                  <View style={[styles.weekTrack, { backgroundColor: colors.border, borderRadius: tokens.radii.full }]}>
                    <View
                      style={[
                        styles.weekFill,
                        {
                          width: `${Math.round((lastWeekTotal / weekMax) * 100)}%` as `${number}%`,
                          backgroundColor: colors.textSecondary,
                          borderRadius: tokens.radii.full,
                        },
                      ]}
                    />
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
  orb: {
    position: 'absolute',
    width: 220,
    height: 220,
    borderRadius: 220,
  },
  eyebrowBadge: {
    alignSelf: 'flex-start',
    borderWidth: 1,
  },
  monthSelector: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderWidth: 1,
  },
  arrowBtn: {
    width: 34,
    height: 34,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroStatsRow: {
    flexDirection: 'row',
    gap: 10,
  },
  heroStatTile: {
    flex: 1,
    padding: 14,
  },
  heroStatLabel: {
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1,
    opacity: 0.72,
  },
  heroStatValue: {
    fontSize: 18,
    marginTop: 6,
  },
  secondaryStatsRow: {
    flexDirection: 'row',
  },
  secondaryStatCard: {
    flex: 1,
  },
  secondaryStatLabel: {
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  dailyBarWrap: {
    alignItems: 'center',
    gap: 6,
    width: 22,
  },
  dailyBar: {
    width: 14,
  },
  merchantRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  merchantLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    flex: 1,
  },
  rankBadge: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  weekHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  weekCallout: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  weekLabelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  weekTrack: {
    height: 10,
    overflow: 'hidden',
  },
  weekFill: {
    height: 10,
  },
  emptyIcon: {
    width: 58,
    height: 58,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
