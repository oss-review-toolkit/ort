#include "lib/cookie-jar.h"
#include "gtest/gtest.h"

TEST(CookieTest, TakingOut) {
    int num_cookies = grab_cookies();
    EXPECT_EQ(num_cookies, 42);
}

