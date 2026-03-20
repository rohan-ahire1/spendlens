import { Platform } from 'react-native';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '../../theme/provider';

export const unstable_settings = {
  initialRouteName: 'index',
};

export default function TabsLayout() {
  const { colors, tokens } = useTheme();

  return (
    <Tabs
      initialRouteName="index"
      screenOptions={{
        headerShown: false,
        sceneStyle: {
          backgroundColor: colors.background,
        },
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarStyle: {
          backgroundColor: colors.tabBar,
          borderTopColor: colors.tabBarBorder,
          borderTopWidth: 1,
          position: 'absolute',
          left: tokens.spacing.md,
          right: tokens.spacing.md,
          bottom: tokens.spacing.md,
          height: 72,
          borderRadius: tokens.radii.xl,
          paddingTop: 8,
          paddingBottom: Platform.OS === 'ios' ? 14 : 10,
          shadowColor: colors.text,
          shadowOffset: { width: 0, height: 12 },
          shadowOpacity: 0.1,
          shadowRadius: 20,
          elevation: 10,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
          fontFamily: tokens.typography.families.accent,
          marginTop: 2,
        },
        tabBarItemStyle: {
          paddingVertical: 4,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons
              name={focused ? 'sparkles' : 'sparkles-outline'}
              size={20}
              color={color}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="analytics"
        options={{
          title: 'Analytics',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons
              name={focused ? 'stats-chart' : 'stats-chart-outline'}
              size={20}
              color={color}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="history"
        options={{
          title: 'History',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons
              name={focused ? 'time' : 'time-outline'}
              size={20}
              color={color}
            />
          ),
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: 'Settings',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons
              name={focused ? 'options' : 'options-outline'}
              size={20}
              color={color}
            />
          ),
        }}
      />
    </Tabs>
  );
}
