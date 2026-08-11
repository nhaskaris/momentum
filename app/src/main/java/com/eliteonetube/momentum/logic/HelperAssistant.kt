package com.eliteonetube.momentum.logic

object HelperAssistant {
    fun ask(question: String): String {
        val q = question.lowercase()
        return when {
            q.contains("weight") || q.contains("loss") || q.contains("lose") || q.contains("cut") -> 
                "To lose weight, set your goal to 'Cut'. I'll give you a calorie deficit based on your activity!"
            q.contains("gain") || q.contains("bulk") || q.contains("muscle") -> 
                "To build muscle, set your goal to 'Bulk'. A slight surplus will help you grow!"
            q.contains("protein") -> 
                "Protein is the building block of muscle. Aim for 1.6g to 2.2g per kg of body weight!"
            q.contains("carb") || q.contains("carbohydrate") -> 
                "Carbs provide energy for your workouts. Focus on complex sources like oats and potatoes!"
            q.contains("fat") -> 
                "Healthy fats are essential for hormone health. Get them from avocados, nuts, and oils!"
            q.contains("macro") -> 
                "Macros are Protein, Carbs, and Fats. Together they make up your total daily calories!"
            q.contains("privacy") || q.contains("data") || q.contains("local") -> 
                "Your data is stored 100% locally on this device. No cloud, no tracking!"
            q.contains("log") || q.contains("food") -> 
                "Go to the Nutrition tab and tap the '+' button or scan a barcode to log what you eat!"
            q.contains("workout") || q.contains("train") || q.contains("exercise") -> 
                "In the Workouts tab, you can start a session from a template or create your own routine!"
            else -> "I'm not sure about that yet, but I'm learning!"
        }
    }
}
