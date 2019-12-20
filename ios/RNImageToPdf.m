//
//  RNImageToPdf.m
//  pdfConverter
//
//  Created by Philipp Müller on 22/09/2017.
//  Copyright © 2017 Anyline. All rights reserved.
//

#import "RNImageToPdf.h"
#import <React/RCTLog.h>

@interface RNImageToPdf ()
@property (strong, nonatomic) NSMutableArray *imageViewArray;
@property (strong, nonatomic) NSMutableDictionary *resultDict;

@end

@implementation RNImageToPdf {
    RCTPromiseResolveBlock _resolveBlock;
    RCTPromiseRejectBlock _rejectBlock;
}

// This RCT (React) "macro" exposes the current module to JavaScript
RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(createPDFbyImages:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    _resolveBlock = resolve;
    _rejectBlock = reject;
    
    self.resultDict = [[NSMutableDictionary alloc] init];
    self.imageViewArray = [[NSMutableArray alloc] init];
    
    NSString *filename = [options objectForKey:@"name"];
    
    NSArray *imagePathArray = [options objectForKey:@"imagePaths"];
    
    NSDictionary *maxSize = [options objectForKey:@"maxSize"];
    long maxWidth = 0;
    long maxHeight = 0;
    if (maxSize) {
        maxWidth = [[maxSize objectForKey:@"width"] longValue];
        maxHeight = [[maxSize objectForKey:@"height"] longValue];
    }
    
    float quality = [[options objectForKey:@"quality"] floatValue];
    int dpi = [options objectForKey:@"dpi"] ? [[options objectForKey:@"dpi"] intValue] : 72;
    
    for (NSString *imagePath in imagePathArray) {
        UIImage *image = [UIImage imageWithContentsOfFile:imagePath];
        
        CGSize size = [self calculateSizeOn:image usingMaxWidth:maxWidth andMaxHeight:maxHeight];
        UIImage *resizedImg = [self imageWith:image scaledToSize:size];
        if (quality > 0) {
            resizedImg = [self compress:resizedImg withQuality:quality];
        }
        
        UIImageView *imageView = [[UIImageView alloc] initWithImage:resizedImg];
        [self.imageViewArray addObject:imageView];
    }
    
    [self createPDFWithFilename:filename dpi:dpi];
    NSLog(@"PDF was created successfully");
}

- (CGSize)calculateSizeOn:(UIImage *)image usingMaxWidth:(long)maxWidth andMaxHeight:(long)maxHeight {
    CGSize size = [image size];
    if (maxWidth == 0 || maxHeight == 0) return size;
    if (size.width <= maxWidth && size.height <= maxHeight) return size;
    
    float ar = size.height/size.width;
    float height = round(maxWidth * ar) < maxHeight ? round(maxWidth*ar) : (float)maxHeight;
    float width = round(height/ar);
    return CGSizeMake(width, height);
}

- (UIImage *)imageWith:(UIImage *)image scaledToSize:(CGSize)newSize {
    RCTLog(@"Image at resolution (w,h) %f,%f", newSize.width, newSize.height);
    CGSize currentSize = [image size];
    if (currentSize.width == newSize.width && currentSize.height == newSize.height) return image;
    
    UIGraphicsBeginImageContext(newSize);
    [image drawInRect:CGRectMake(0, 0, newSize.width, newSize.height)];
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
}

- (UIImage *)compress:(UIImage *)image withQuality:(float)quality{
    return [UIImage imageWithData:UIImageJPEGRepresentation(image, quality)];
}


- (void)createPDFWithFilename:(NSString *)filename dpi:(int)dpi{
    
    const float pdfScale = 72.0/dpi;
    
    UIImageView *firstImageView = self.imageViewArray.firstObject;
    //Start with pdf:
    NSMutableData *pdfData = [NSMutableData data];
    //Start pdf context;
    const CGRect bounds = CGRectMake(firstImageView.bounds.origin.x,
                               firstImageView.bounds.origin.y,
                               round(firstImageView.bounds.size.width*pdfScale),
                               round(firstImageView.bounds.size.height*pdfScale));
    
    UIGraphicsBeginPDFContextToData(pdfData, bounds, nil);
    
    CGContextRef pdfContext;
    for (UIImageView *iv in self.imageViewArray) {
        //Start new page with image bounds:
        UIGraphicsBeginPDFPageWithInfo(bounds, nil);
        pdfContext = UIGraphicsGetCurrentContext();
        
        CGContextSetInterpolationQuality(pdfContext, kCGInterpolationHigh);
        //        CGContextTranslateCTM(pdfContext, 0.0, 0.0);
        CGContextScaleCTM(pdfContext, pdfScale, pdfScale);
        CGContextSaveGState(pdfContext);
        
        //Render image layer to context:
        [iv.layer renderInContext:pdfContext];
    }
    
    // remove PDF rendering context
    UIGraphicsEndPDFContext();
    NSArray *documentDirectories = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask,YES);
    NSString *documentDirectory = [documentDirectories objectAtIndex:0];
    NSString *documentDirectoryFilename = [documentDirectory stringByAppendingPathComponent:[NSString stringWithFormat:@"%@.pdf", filename]];
    
    RCTLog(@"filePath: %@",documentDirectoryFilename);
    if (!pdfData) {
        _rejectBlock(RCTErrorUnspecified, nil, RCTErrorWithMessage(@"PDF couldn't be saved."));
        return;
    }
    
    //Write pdf file
    [pdfData writeToFile:documentDirectoryFilename atomically:YES];
    
    RCTLog(@"Wrote %llu bytes", (unsigned long long)[pdfData length]);
    //Add filepath to resultDict. This will be send back to RN
    [self.resultDict setObject:documentDirectoryFilename forKey:@"filePath"];
    
    _resolveBlock(self.resultDict);
}

@end

