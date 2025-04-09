package com.example.chooserecipientxml.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.chooserecipientxml.databinding.ActivityContactDetailBinding
import com.example.chooserecipientxml.model.Contact

class ContactDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the contact passed via intent
        val contact = intent.getSerializableExtra("CONTACT") as? Contact
        contact?.let {
            binding.contactName.text = it.name
            binding.contactNumber.text = it.token
            // Populate other contact details as needed
        }
    }
}
