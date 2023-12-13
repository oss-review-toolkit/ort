package main

import (
    "context"

    "go.mongodb.org/mongo-driver/mongo"
    "go.mongodb.org/mongo-driver/mongo/options"
)


func main() {
    clientOptions := options.Client().ApplyURI("mongodb://localhost:27017")
    _, err := mongo.Connect(context.TODO(), clientOptions)
    print(err)
}


