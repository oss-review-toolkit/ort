{-# LANGUAGE OverloadedStrings #-}
module Handler.Comment where

import Import
import Data.Aeson

newtype Comment = Comment Text

instance ToJSON Comment where
    toJSON (Comment t) = object ["message" .= t]
instance FromJSON Comment where
    parseJSON = withObject "Comment" $ \o -> Comment <$> o .: "message"

postCommentR :: Handler Value
postCommentR = do
    -- requireCheckJsonBody will parse the request body into the appropriate type, or return a 400 status code if the request JSON is invalid.
    comment <- requireCheckJsonBody :: Handler Comment

    returnJson comment
