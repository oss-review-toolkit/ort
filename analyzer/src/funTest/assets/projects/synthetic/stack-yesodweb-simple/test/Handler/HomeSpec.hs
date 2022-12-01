{-# LANGUAGE NoImplicitPrelude #-}
{-# LANGUAGE OverloadedStrings #-}
module Handler.HomeSpec (spec) where

import TestImport

spec :: Spec
spec = withApp $ do

    describe "Homepage" $ do
      it "loads the index and checks it looks right" $ do
          get HomeR
          statusIs 200
          htmlAnyContain "h1" "a modern framework for blazing fast websites"

          request $ do
              setMethod "POST"
              setUrl HomeR
              addToken
              fileByLabelExact "Choose a file" "test/Spec.hs" "text/plain" -- talk about self-reference
              byLabelExact "What's on the file?" "Some Content"

          -- more debugging printBody
          htmlAllContain ".upload-response" "text/plain"
          htmlAllContain ".upload-response" "Some Content"
