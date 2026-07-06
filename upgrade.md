Give me complete prompt without loosing anything for cursor 
Build a complete smart scan enhancement engine with two separate processing modes: Clean and AI Clean.
 
SERVER HARDWARE FOR AI CLEAN:
 
 
- CPU: Intel Core i7-14700
 
- GPU: Intel Arc 16GB
 
- RAM: 64GB DDR5
 
- Storage: 2TB NVMe SSD
 

 
PRIMARY GOAL: Build a smart scan enhancement engine capable of producing clean, readable, premium-quality document scans with advanced cleanup, dewarp, enhancement, restoration, OCR optimization, and visual preservation.
 
FREE USERS: Use Clean mode with fully offline Android processing.
 
PREMIUM USERS: Use AI Clean mode through AI server.
 
TARGET OUTPUT:
 
 
- clean scans
 
- premium scans
 
- maximum readability
 
- preserve authenticity
 
- preserve important visual elements
 
- maintain machine readability
 
- preserve critical document integrity
 

 
SUPPORTED DOCUMENT TYPES:
 
Standard:
 
 
- documents
 
- books
 
- forms
 
- certificates
 
- handwritten notes
 
- colorful pages
 
- patterned papers
 

 
Business:
 
 
- invoices
 
- receipts
 
- reports
 
- contracts
 
- business cards
 
- bills
 
- statements
 

 
Education:
 
 
- notebooks
 
- worksheets
 
- assignments
 
- exam papers
 
- textbooks
 
- lecture notes
 

 
Official:
 
 
- IDs
 
- passports
 
- licenses
 
- legal documents
 
- government forms
 

 
Creative:
 
 
- magazines
 
- brochures
 
- posters
 
- comics
 
- newspapers
 
- flyers
 

 
Special:
 
 
- old documents
 
- damaged papers
 
- faded papers
 
- folded papers
 
- wrinkled papers
 
- laminated papers
 
- glossy papers
 

 
PRESERVE THESE ELEMENTS:
 
Critical:
 
 
- text
 
- handwriting
 
- signatures
 
- logos
 
- seals
 
- stamps
 

 
Visual:
 
 
- colors
 
- emojis
 
- stars
 
- icons
 
- decorative lines
 
- symbols
 

 
Identity:
 
 
- watermarks
 
- background paper design
 
- official markings
 
- borders
 
- highlights
 

 
Machine-readable:
 
 
- QR codes
 
- barcodes
 
- serial numbers
 

 
Special:
 
 
- pen marks
 
- underlines
 
- charts
 
- graphs
 
- tables
 
- sketches
 

 
REMOVE THESE ARTIFACTS:
 
Human:
 
 
- fingers
 
- hands
 
- shadows
 

 
Camera:
 
 
- blur noise
 
- sensor noise
 
- glare
 
- reflections
 
- lens distortion
 
- perspective distortion
 

 
Environment:
 
 
- dust
 
- dirt
 
- stains
 
- accidental objects
 
- clutter
 
- desk texture bleed
 

 
AI SERVER STACK:
 
 
- Python 3.11
 
- OpenCV
 
- NumPy
 
- FastAPI
 
- PyTorch
 
- ONNX Runtime
 
- OpenVINO
 
- Intel oneAPI
 

 
AI MODELS:
 
 
- YOLOv8
 
- U-2-Net
 
- DewarpNet
 
- Pix2Pix / CycleGAN
 
- SwinIR
 
- Real-ESRGAN
 
- PaddleOCR
 

 
# ================================================ SMART PRE-PROCESS ANALYZER
 
Run document analysis before processing.
 
Analyze:
 
 
- document type
 
- paper quality
 
- lighting quality
 
- blur severity
 
- wrinkle severity
 
- color importance
 
- text density
 
- handwriting presence
 
- machine-readable elements presence
 
- damage severity
 

 
Purpose: Automatically route scan to best pipeline.

# ================================================ ADVANCED CURVE DETECTION & LOCAL STRAIGHTENING ENGINE

Critical Requirement:
Engine must detect and correct both global and local page curvature during:

* live scan stage
* post-scan editing stage

Must support:

* documents
* books
* IDs
* passports
* receipts
* notebooks
* folded papers
* broken pages
* rolled papers
* curled papers

Must detect:

* curved pages
* random page bending
* partial page warping
* rolled papers
* folded corners
* broken pages
* curled book edges
* bent IDs
* warped receipts
* wavy papers
* uneven writing baselines
* local text distortion

Common Examples:

* book scanned with center curvature
* receipt rolled at edges
* ID card slightly bent
* folded paper causing warped text
* notebook page with random wrinkles
* curled document corners
* writing lines slanted due to page bending

CURVE DETECTION TYPES:

1. Global Curve Detection
   Detect:

* perspective skew
* cylindrical bend
* book center fold
* page rolling
* full surface warp

2. Local Curve Detection
   Detect:

* corner folds
* wrinkles
* random bends
* edge curls
* localized warping

3. Writing Baseline Detection
   Detect:

* curved text lines
* slanted handwriting
* uneven row alignment
* warped writing regions

ANALYSIS METHODS:

* contour analysis
* surface geometry estimation
* line detection
* baseline tracking
* mesh warping analysis
* OCR-guided line alignment

PROCESSING LOGIC:

Stage 1:
Detect global geometry

Stage 2:
Detect local geometry distortion

Stage 3:
Generate deformation map

Stage 4:
Apply adaptive straightening

Stage 5:
Validate text alignment

Stage 6:
Preserve authenticity

DEWARP MODES:

Light Dewarp:

* mild correction
* preserve natural look

Balanced Dewarp:

* moderate correction
* improve readability

Strong Dewarp:

* aggressive correction
* maximum straightening

Adaptive Rules:

Text-heavy documents:
Aggressive straightening allowed

Handwritten notes:
Moderate straightening only

Official IDs:
Preserve geometry carefully

Books:
Strong center-fold correction

Receipts:
Strong curl correction

Important:
Do NOT over-straighten if it damages:

* handwriting
* signatures
* stamps
* seals
* QR codes

SUCCESS TARGET:

* text lines visually straight
* writing alignment improved
* curved pages naturally flattened
* document authenticity preserved
 
# ================================================ DOCUMENT CLASSIFICATION ENGINE
 
Classify into:
 
 
- text-heavy
 
- visual-heavy
 
- mixed-content
 
- official-ID
 
- machine-readable
 
- handwritten
 
- damaged-document
 

 
Routing Rules:
 
Text-heavy: Prioritize OCR readability
 
Visual-heavy: Prioritize color preservation
 
Mixed-content: Balanced optimization
 
Official-ID: Preserve authenticity aggressively
 
Machine-readable: Preserve QR/barcode integrity
 
Handwritten: Preserve stroke sharpness
 
Damaged-document: Prioritize restoration
 
PROCESSING PIPELINE:

1. document detection
2. document segmentation
3. global curve analysis
4. local curve detection
5. deformation mapping
6. dewarp / straightening
7. artifact detection
8. shadow removal
9. denoise
10. enhancement
11. OCR validation
12. final optimization

 
UI FLOW:
 
After scan: Show: [ Clean ]   [ AI Clean ]
 
Also show: Recommended Mode Recommended Level
 
Example: Recommended: AI Clean Level 5
 
If user selects Clean: Show Clean previews and Clean slider.
 
If user selects AI Clean: Show AI Clean previews and AI Clean slider.
 
# ================================================ CLEAN MODE
 
Runs fully inside Android app.
 
Purpose:
 
 
- fast
 
- offline
 
- natural look
 
- lightweight processing
 

 
Preview: LEFT = Natural Clean RIGHT = Maximum Clean
 
Slider: 0 1 2 3 4 5 6 7
 
Important: Levels 1–4 = Natural-to-Visual progression Levels 5–7 = Visual-to-Maximum cleanup progression
 
CLEAN LEVEL LOGIC:
 
Level 0: Original scan
 
Level 1: Natural Clean
 
 
- crop
 
- edge detect
 
- dewarp
 
- remove fingers
 
- remove shadows
 
- remove random noise
 
- preserve original colors
 
- no color enhancement
 

 
Goal: Natural clean scan.
 
Level 2: Natural Clean+
 
 
- level 1 processing
 
- slight brightness boost
 
- slight contrast boost
 
- slight color boost
 

 
Level 3: Enhanced Natural
 
 
- stronger contrast
 
- stronger vibrance
 
- stronger color enhancement
 
- cleanup slightly reduced
 

 
Level 4: Maximum Color Richness
 
 
- strongest color enhancement
 
- strongest vibrance
 
- strongest contrast
 
- cleanup reduced
 
- preserve visual richness
 

 
Best for:
 
 
- certificates
 
- colorful pages
 
- magazines
 

 
Level 5: Balanced Pro
 
 
- slightly reduce color enhancement
 
- cleanup increases again
 
- stronger readability optimization
- advanced global dewarp
- local region straightening
- AI mesh-based page flattening
- OCR-guided writing alignment correction
 

 
Level 6: Strong Clean Pro
 
 
- strong cleanup
 
- strong enhancement
 
- aggressive dewarp
 
- stronger sharpening
- local curve correction
- writing straightening
- adaptive page flattening
- advanced global dewarp
- local region straightening
- AI mesh-based page flattening
- OCR-guided writing alignment correction
 

 
Level 7: Maximum Clean
 
 
- maximum offline cleanup
 
- maximum enhancement
 
- best readability
 
- strongest sharpening
 - local curve correction
- writing straightening
- adaptive page flattening
- advanced global dewarp
- local region straightening
- AI mesh-based page flattening
- OCR-guided writing alignment correction
 

 
CLEAN PERFORMANCE TARGET: Level 1–3: < 400ms
 
Level 4–5: < 700ms
 
Level 6–7: < 1200ms
# ================================================ AI CLEAN MODE
 
Runs on AI server.
 
Purpose:
 
 
- premium quality
 
- advanced restoration
 
- best scan output
 

 
Preview: LEFT = AI Natural Clean RIGHT = AI Maximum Quality
 
Slider: 0 1 2 3 4 5 6 7
 
AI CLEAN LEVEL LOGIC:
 
Important: Each higher level includes processing from previous levels plus additional AI enhancement.
 
Processing dimensions:
 
 
- cleanup strength
 
- dewarp strength
 
- artifact removal
 
- color optimization
 
- restoration quality
 
- OCR optimization
 

 
# ================================================ Level 0: Original Scan
 
 
- no processing
 
- raw scan preview
 

 
Use:
 
 
- compare against enhanced versions
 

 
# ================================================ Level 1: AI Natural Clean
 
Purpose: Natural premium cleanup with minimal visual change.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 

 
Apply:
 
 
- document detection
 
- contour detection
 
- crop correction
 
- light perspective correction
 
- light finger removal
 
- light shadow cleanup
 
- light noise removal
 

 
Color:
 
 
- original colors preserved
 
- no enhancement
 

 
Cleanup Strength: 10%
 
Dewarp Strength: 10%
 
Enhancement Strength: 0%
 
Best For:
 
 
- normal documents
 
- clean papers
 
- users preferring natural look
 

 
Goal: Looks almost original but cleaner.
 
# ================================================ Level 2: AI Light Enhancement
 
Purpose: Natural scan with visible improvement.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 
- light OCR validation
 

 
Apply:
 
 
- Level 1 processing
 
- improved edge refinement
 
- better shadow removal
 
- stronger background cleanup
 
- slight readability optimization
 

 
Color:
 
 
- slight brightness boost
 
- slight contrast boost
 

 
Cleanup Strength: 25%
 
Dewarp Strength: 20%
 
Enhancement Strength: 10%
 
Best For:
 
 
- office papers
 
- forms
 
- contracts
 

 
Goal: Natural but noticeably cleaner.
 
# ================================================ Level 3: AI Enhanced Natural
 
Purpose: Strong readability improvement while preserving natural appearance.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 
- PaddleOCR
 

 
Apply:
 
 
- Level 2 processing
 
- stronger cleanup
 
- stronger shadow removal
 
- better dewarp
 
- OCR-guided text refinement
 

 
Color:
 
 
- slight vibrance boost
 
- moderate contrast boost
 

 
Cleanup Strength: 40%
 
Dewarp Strength: 35%
 
Enhancement Strength: 25%
 
Best For:
 
 
- documents
 
- invoices
 
- receipts
 

 
Goal: Text becomes clearer and easier to read.
 
# ================================================ Level 4: AI Rich Visual
 
Purpose: Color-focused enhancement with premium visual quality.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 
- PaddleOCR
 
- Real-ESRGAN (light)
 

 
Apply:
 
 
- Level 3 processing
 
- advanced visual enhancement
 
- richer colors
 
- detail sharpening
 
- preserve design elements
 

 
Color:
 
 
- strong color enhancement
 
- strong vibrance
 
- strong contrast
 

 
Cleanup Strength: 45%
 
Dewarp Strength: 45%
 
Enhancement Strength: 50%
 
Best For:
 
 
- certificates
 
- colorful pages
 
- magazines
 
- brochures
 

 
Goal: Visually impressive premium scan.
 
# ================================================ Level 5: AI Balanced Pro
 
Purpose: Balanced cleanup + enhancement + restoration.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 
- DewarpNet
 
- PaddleOCR
 
- Real-ESRGAN
 

 
Apply:
 
 
- advanced cleanup
 
- advanced dewarp
 
- stronger sharpening
 
- stronger readability optimization
 

 
Color:
 
 
- balanced enhancement
 
- controlled richness
 

 
Cleanup Strength: 65%
 
Dewarp Strength: 60%
 
Enhancement Strength: 65%
 
Best For:
 
 
- mixed documents
 
- business papers
 
- certificates
 

 
Goal: Strong professional scan quality.
 
# ================================================ Level 6: AI Strong Pro
 
Purpose: Heavy restoration for difficult scans.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 
- DewarpNet
 
- SwinIR
 
- Real-ESRGAN
 
- PaddleOCR
 

 
Apply:
 
 
- aggressive dewarp
 
- heavy denoise
 
- advanced artifact removal
 
- blur correction
 
- detail restoration
 

 
Color:
 
 
- intelligent adaptive enhancement
 

 
Cleanup Strength: 85%
 
Dewarp Strength: 85%
 
Enhancement Strength: 80%
 
Best For:
 
 
- books
 
- folded papers
 
- wrinkled documents
 
- poor lighting scans
 

 
Goal: Restore difficult scans into clean readable documents.
 
# ================================================ Level 7: AI Maximum Quality
 
Purpose: Highest premium AI processing.
 
Active Models:
 
 
- YOLOv8
 
- U-2-Net
 
- DewarpNet
 
- Pix2Pix / CycleGAN
 
- SwinIR
 
- Real-ESRGAN
 
- PaddleOCR
 

 
Apply:
 
 
- full AI pipeline
 
- maximum dewarp
 
- maximum cleanup
 
- maximum restoration
 
- maximum enhancement
 
- OCR-guided optimization
 
- strongest artifact removal
 
- premium final refinement
 

 
Color:
 
 
- intelligent content-aware optimization
 

 
Cleanup Strength: 100%
 
Dewarp Strength: 100%
 
Enhancement Strength: 100%
 
Best For:
 
 
- damaged papers
 
- difficult book scans
 
- faded documents
 
- premium users needing best output
 

 
Goal: Highest possible scan quality.
 
AI PERFORMANCE TARGET: Level 1–3: 1–2 sec
 
Level 4–5: 2–4 sec
 
Level 6–7: 4–8 sec
 
# ================================================ OCR VALIDATION ENGINE
 
Run OCR before and after enhancement.
 
Measure:
 
 
- OCR confidence
 
- text clarity
 
- edge sharpness
 
- contrast score
 

 
Rule: If enhancement reduces OCR confidence: Rollback to safer enhancement.
 
# ================================================ AI HALLUCINATION PREVENTION RULES
 
AI must NEVER:
 
 
- invent text
 
- generate missing letters
 
- modify signatures
 
- modify seals
 
- modify numbers
 
- alter IDs
 
- alter QR code structure
 

 
Critical rule: Authenticity > Beauty
 
# ================================================ MACHINE-READABLE PROTECTION RULES
 
Critical for:
 
 
- QR codes
 
- barcodes
 
- serial numbers
 

 
Requirements:
 
 
- preserve geometry
 
- preserve edge clarity
 
- avoid warping
 
- preserve scan accuracy
 

 
Validation: Run decode test after enhancement.
 
If decode fails: Rollback to safer processing.
 
# ================================================ QUALITY RULES
 
Avoid:
 
 
- over sharpening
 
- artificial colors
 
- broken text edges
 
- over whitening
 
- lost signatures
 
- lost stamps
 
- halo artifacts
 
- ringing artifacts
 

 
SMART ADAPTIVE RULES:
 
For text documents: Prioritize readability
 
For books: Prioritize dewarp
 
For certificates: Preserve colors and seals
 
For handwritten notes: Preserve pen strokes
 
For magazines: Preserve visual richness
 
# ================================================ SERVER OPTIMIZATION RULES
 
Use:
 
 
- ONNX Runtime acceleration
 
- OpenVINO optimization
 
- FP16 inference
 
- safe INT8 quantization
 
- model caching
 
- pipeline batching
 

 
Keep Always Loaded:
 
 
- YOLOv8
 
- U-2-Net
 

 
Load On Demand:
 
 
- DewarpNet
 
- SwinIR
 
- Real-ESRGAN
 
- Pix2Pix
 

 
# ================================================ FAILSAFE SYSTEM
 
If AI stage fails: Fallback gracefully.
 
Examples: If DewarpNet fails: Use OpenCV dewarp
 
If OCR fails: Skip OCR optimization
 
If GPU overloaded: Use lighter model
 
Never fail full scan. Always return usable output.
 
# ================================================ EXPORT PROFILES
 
Provide:
 
 
- Document Mode
 
- Print Mode
 
- Archive Mode
 
- OCR Mode
 

 
# ================================================ FINAL MASTER RULE
 
Priority Order:
 
 
1. Preserve authenticity
 
2. Preserve critical content
 
3. Improve readability
 
4. Remove artifacts
 
5. Improve aesthetics
 

 
SUCCESS METRIC: Clean mode should feel fast and natural. AI Clean should feel premium and significantly smarter. Level 1 should look natural. Level 7 should look premium and powerful.