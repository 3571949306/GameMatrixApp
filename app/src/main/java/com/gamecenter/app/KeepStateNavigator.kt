package com.gamecenter.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.FragmentNavigator

@Navigator.Name("keep_state_fragment")
class KeepStateNavigator(
    private val context: Context,
    private val manager: FragmentManager,
    private val containerId: Int
) : Navigator<FragmentNavigator.Destination>() {

    companion object {
        private const val TAG = "KeepStateNavigator"
    }

    override fun createDestination(): FragmentNavigator.Destination {
        return FragmentNavigator.Destination(this)
    }

    override fun navigate(
        destination: FragmentNavigator.Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ): NavDestination? {
        if (manager.isStateSaved) {
            Log.w(TAG, "Ignoring navigate() call: FragmentManager state already saved")
            return null
        }

        val className = destination.className
        val tag = "fragment-${destination.id}"

        val currentFragment = manager.findFragmentByTag(tag)

        val transaction = manager.beginTransaction()

        manager.primaryNavigationFragment?.let { currentPrimary ->
            if (currentPrimary != currentFragment) {
                transaction.hide(currentPrimary)
            }
        }

        if (currentFragment != null) {
            transaction.show(currentFragment)
            transaction.setPrimaryNavigationFragment(currentFragment)
        } else {
            val fragment = manager.fragmentFactory.instantiate(context.classLoader, className)
            if (args != null) fragment.arguments = args

            transaction.add(containerId, fragment, tag)
            transaction.setPrimaryNavigationFragment(fragment)
        }

        transaction.setReorderingAllowed(true)
        transaction.commitNowAllowingStateLoss()

        return destination
    }

    override fun popBackStack(): Boolean {
        if (manager.isStateSaved) return false

        val currentPrimary = manager.primaryNavigationFragment ?: return false
        val transaction = manager.beginTransaction()
        transaction.remove(currentPrimary)

        val previousFragment = findPreviousFragment(currentPrimary)
        if (previousFragment != null) {
            transaction.show(previousFragment)
            transaction.setPrimaryNavigationFragment(previousFragment)
        }

        transaction.setReorderingAllowed(true)
        transaction.commitNowAllowingStateLoss()
        return true
    }

    private fun findPreviousFragment(current: Fragment): Fragment? {
        val fragments = manager.fragments
        val currentIndex = fragments.indexOf(current)
        if (currentIndex <= 0) return null
        for (i in currentIndex - 1 downTo 0) {
            val f = fragments[i]
            if (f != current && !f.isRemoving) return f
        }
        return null
    }
}
