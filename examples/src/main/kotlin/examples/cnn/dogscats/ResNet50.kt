/*
 * Copyright 2020 JetBrains s.r.o. and Kotlin Deep Learning project contributors. All Rights Reserved.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package examples.cnn.dogscats

import org.jetbrains.kotlinx.dl.api.core.Functional
import org.jetbrains.kotlinx.dl.api.core.activation.Activations
import org.jetbrains.kotlinx.dl.api.core.loss.Losses
import org.jetbrains.kotlinx.dl.api.core.metric.Metrics
import org.jetbrains.kotlinx.dl.api.core.model.resnet50Light
import org.jetbrains.kotlinx.dl.api.core.optimizer.Adam
import org.jetbrains.kotlinx.dl.api.core.summary.logSummary
import org.jetbrains.kotlinx.dl.dataset.OnFlyImageDataset
import org.jetbrains.kotlinx.dl.dataset.dogsCatsDatasetPath
import org.jetbrains.kotlinx.dl.dataset.image.ColorOrder
import org.jetbrains.kotlinx.dl.dataset.preprocessor.*
import org.jetbrains.kotlinx.dl.dataset.preprocessor.generator.FromFolders
import org.jetbrains.kotlinx.dl.dataset.preprocessor.image.InterpolationType
import org.jetbrains.kotlinx.dl.dataset.preprocessor.image.resize
import java.io.File
import kotlin.reflect.KFunction4

private const val EPOCHS = 20
private const val TRAINING_BATCH_SIZE = 64
private const val TEST_BATCH_SIZE = 32
private const val NUM_CLASSES = 2
private const val NUM_CHANNELS = 3L
private const val IMAGE_SIZE = 64L
private const val TRAIN_TEST_SPLIT_RATIO = 0.8

/**
 * This example shows how to do image classification from scratch using pre-made [resnet50Light] model, without leveraging pre-trained weights.
 * We demonstrate the workflow on the Kaggle Cats vs Dogs binary classification dataset.
 *
 * We use the [Preprocessing] DSL to describe the dataset generation pipeline.
 *
 * It includes:
 * - dataset loading from S3
 * - preprocessing DSL declaration
 * - [OnFlyImageDataset] dataset creation
 * - dataset splitting
 * - usage of pre-made model from [org.jetbrains.kotlinx.dl.api.core.model] package
 * - model compilation
 * - model training
 * - model evaluation
 */
fun resnet50onDogsVsCatsDataset() {
    val modelBuilderFunction = ::resnet50Light
    runResNetTraining(modelBuilderFunction)
}

internal fun runResNetTraining(modelBuilderFunction: KFunction4<Long, Int, Long, Activations, Functional>) {
    val dogsVsCatsDatasetPath = dogsCatsDatasetPath()

    val preprocessing: Preprocessing = preprocess {
        load {
            pathToData = File(dogsVsCatsDatasetPath)
            imageShape = ImageShape(channels = NUM_CHANNELS)
            colorMode = ColorOrder.BGR
            labelGenerator = FromFolders(mapping = mapOf("cat" to 0, "dog" to 1))
        }
        transformImage {
            resize {
                outputHeight = IMAGE_SIZE.toInt()
                outputWidth = IMAGE_SIZE.toInt()
                interpolation = InterpolationType.BILINEAR
            }
        }
        transformTensor {
            rescale {
                scalingCoefficient = 255f
            }
        }
    }

    val dataset = OnFlyImageDataset.create(preprocessing).shuffle()
    val (train, test) = dataset.split(TRAIN_TEST_SPLIT_RATIO)

    val model = modelBuilderFunction.invoke(IMAGE_SIZE, NUM_CLASSES, NUM_CHANNELS, Activations.Linear)
    model.use {
        it.compile(
            optimizer = Adam(),
            loss = Losses.SOFT_MAX_CROSS_ENTROPY_WITH_LOGITS,
            metric = Metrics.ACCURACY
        )

        it.logSummary()

        val start = System.currentTimeMillis()
        it.fit(dataset = train, epochs = EPOCHS, batchSize = TRAINING_BATCH_SIZE)
        println("Training time: ${(System.currentTimeMillis() - start) / 1000f}")

        val accuracy = it.evaluate(dataset = test, batchSize = TEST_BATCH_SIZE).metrics[Metrics.ACCURACY]

        println("Accuracy: $accuracy")
    }
}

/** */
fun main(): Unit = resnet50onDogsVsCatsDataset()
