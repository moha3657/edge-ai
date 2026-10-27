package com.example.service

data class SampleDocItem(
    val id: String,
    val title: String,
    val type: String,
    val preview: String,
    val fullContent: String
)

object SampleDataRepository {
    val sampleDocs = listOf(
        SampleDocItem(
            id = "sample_financial_pdf",
            title = "Quarterly_Financial_Report_Q3.pdf",
            type = "PDF",
            preview = "Q3 Financial Statement: $14.2M revenue, $5.6M net income, edge infra savings...",
            fullContent = """
                TechEdge Inc. - Quarterly Financial Statement (Q3 2025)
                EXECUTIVE SUMMARY:
                During the third quarter of 2025, TechEdge recorded total consolidated revenues of $14.2 million, representing a 28% year-over-year increase compared to $11.1 million in Q3 2024.
                
                OPERATING EXPENSES & MARGINS:
                Total operating expenses were $8.6 million, down 12% from the preceding quarter. The primary driver of efficiency was the complete migration from server-side cloud LLM APIs to on-device zero-concurrency edge AI running on client devices with 4GB RAM. Server hosting fees dropped by $1.8 million annually.
                
                NET INCOME & LIQUIDITY:
                Net income for the quarter reached $5.6 million (diluted EPS of $0.44). Cash and cash equivalents at the end of the quarter stood at $22.4 million.
                
                OUTLOOK & GUIDANCE:
                Capital expenditures for Q4 2025 are projected between $1.2M and $1.5M. The company expects continued margin expansion as low-memory local RAG deployment expands across global emerging markets.
            """.trimIndent()
        ),
        SampleDocItem(
            id = "sample_medical_txt",
            title = "Patient_Discharge_Summary.txt",
            type = "TXT",
            preview = "Clinical discharge summary: acute gastroenteritis, medication plan, followup...",
            fullContent = """
                ST. JUDE MEDICAL CENTER - INPATIENT DISCHARGE SUMMARY
                Patient ID: #MRN-849201 | DOB: 1978-04-12
                Primary Diagnosis: Acute viral gastroenteritis with moderate dehydration.
                Secondary: Mild hypokalemia (resolved prior to discharge).
                
                HOSPITAL COURSE:
                Patient presented with a 48-hour history of severe nausea, vomiting, and non-bloody diarrhea. Vital signs at admission: BP 96/60, HR 108 bpm, oral temp 38.3 C.
                Patient was administered 2000 mL intravenous normal saline with supplemental potassium chloride, and intravenous ondansetron 4mg every 8 hours. Tolerated clear liquids after 24 hours.
                
                DISCHARGE MEDICATIONS:
                1. Ondansetron 4mg ODT: 1 tablet sublingually every 8 hours as needed for nausea (max 3 days).
                2. Oral Rehydration Solution (ORS): 200-300 mL after each loose bowel movement.
                3. Avoid NSAIDs (ibuprofen, naproxen) for 72 hours; use acetaminophen 500mg for pain/fever if needed.
                
                FOLLOW-UP PROTOCOL:
                Return to clinic or emergency department immediately if experiencing persistent fever > 38.5 C, intractable vomiting, dark or bloody stools, or severe postural dizziness. Follow-up visit with primary care physician scheduled for 2025-11-04.
            """.trimIndent()
        ),
        SampleDocItem(
            id = "sample_legal_clause",
            title = "Edge_AI_Lease_Agreement.txt",
            type = "TXT",
            preview = "Master License: Clause 4.2 Zero-concurrency RAM lock & 1024 context ceiling...",
            fullContent = """
                MASTER SOFTWARE & ON-DEVICE INFERENCE LICENSE AGREEMENT
                SECTION 4: HARDWARE SPECIFICATIONS & MEMORY GOVERNANCE
                
                Clause 4.1 (Target Device Profile): The software is designed exclusively for client mobile hardware featuring equal to or less than 4,096 Megabytes (4GB) of Random Access Memory (RAM).
                
                Clause 4.2 (Zero-Concurrency Mandate): Under no circumstances shall the Licensee or the runtime container initialize, hold, or execute two or more neural network model weights concurrently. An active model must be completely dereferenced, its native memory buffers deallocated, and system garbage collection invoked prior to allocating memory for any secondary model.
                
                Clause 4.3 (Strict Context Window Ceiling): The maximum allowable KV-cache and token context window for the reasoning model (Model 1: Qwen2.5-1.5B-Instruct Q4_K_M) shall not exceed 1,024 tokens. The prompt formatter must truncate historical input tokens to guarantee that prompt plus generation tokens remain strictly beneath this 1,024 threshold.
                
                Clause 4.4 (Offline Data Sovereignty): The software shall operate 100% offline without initiating outbound HTTP/HTTPS requests to external inference servers or telemetry gateways.
            """.trimIndent()
        ),
        SampleDocItem(
            id = "sample_ocr_receipt",
            title = "Scanned_Medical_Receipt.png",
            type = "IMAGE_OCR",
            preview = "Scanned invoice: Pharmacy clinic Rx, medications, total $40.85...",
            fullContent = """
                [OCR EXTRACTED TEXT FROM SCANNED DOCUMENT]
                STORE: METRO APOTHECARY & CLINIC (Branch #04)
                TRANSACTION DATE: 2025-10-24 16:15:22
                CASHIER: A. Vance | TERMINAL: POS-02
                ----------------------------------------
                1x AMOXICILLIN 500MG CAPSULES (30 ct)    $18.50
                1x ORAL ELECTROLYTE SALTS (Box of 8)     $7.25
                1x DIGITAL INFRARED THERMOMETER          $14.99
                ----------------------------------------
                SUBTOTAL:                                $40.74
                SALES TAX (6.5%):                        $2.65
                TOTAL AMOUNT:                            $43.39
                PAYMENT METHOD: VISA CONTACTLESS (CHIP)
                CARD: ************9482 (AUTH: 039482)
                
                PATIENT NOTICE:
                Take amoxicillin every 8 hours with water. Keep rehydration fluids at room temperature. For questions call clinic pharmacy helpline at 555-0199.
            """.trimIndent()
        )
    )
}
